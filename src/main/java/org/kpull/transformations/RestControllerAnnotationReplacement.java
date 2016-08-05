package org.kpull.transformations;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseException;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.ModifierSet;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.*;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

public class RestControllerAnnotationReplacement {

    public static void main(String[] args) throws Exception {
        InputStream sourceInput;
        if (args.length > 0) {
            sourceInput = new FileInputStream(new File(args[0]));
        } else {
            sourceInput = System.in;
        }
        RestControllerAnnotationReplacement transformation = new RestControllerAnnotationReplacement();
        transformation.process(sourceInput, System.out);
    }

    private ClassOrInterfaceDeclaration classToRemoveAnnotationFrom;
    private List<MethodDeclaration> methodsToRemoveAnnotationFrom;

    public RestControllerAnnotationReplacement() {
        methodsToRemoveAnnotationFrom = new LinkedList<>();
    }

    public void process(InputStream source, PrintStream output) throws IOException, ParseException {
        CompilationUnit currentCompilationTarget = JavaParser.parse(source);
        EligibilityResult result = new EligibilityResult();
        new DetermineEligibilityVisitor().visit(currentCompilationTarget, result);
        if (result.isEligible()) {
            performSourceCodeModifications(currentCompilationTarget);
        }
        output.println(currentCompilationTarget.toString());
    }

    private void performSourceCodeModifications(CompilationUnit currentCompilationTarget) throws ParseException {
        removeControllerAnnotationFromClass();
        removeResponseBodyAnnotationFromRequestMappingMethods();
        removeRedundantImportStatements(currentCompilationTarget);
        currentCompilationTarget.getImports().add(JavaParser.parseImport("import org.springframework.web.bind.annotation.RestController;"));
        classToRemoveAnnotationFrom.getAnnotations().add(JavaParser.parseAnnotation("@RestController"));
    }

    private void removeRedundantImportStatements(CompilationUnit currentCompilationTarget) {
        Iterator<ImportDeclaration> importsIterator = currentCompilationTarget.getImports().iterator();
        while (importsIterator.hasNext()) {
            ImportDeclaration importStatement = importsIterator.next();
            if (importStatement.getName().getName().equals("ResponseBody")) {
                importsIterator.remove();
            } else if (importStatement.getName().getName().equals("Controller")) {
                importsIterator.remove();
            }
        }
    }

    private void removeResponseBodyAnnotationFromRequestMappingMethods() {
        for (MethodDeclaration methodDeclaration : methodsToRemoveAnnotationFrom) {
            ListIterator<AnnotationExpr> annotationsIterator = methodDeclaration.getAnnotations().listIterator();
            while (annotationsIterator.hasNext()) {
                AnnotationExpr annotation = annotationsIterator.next();
                if (annotation.getName().getName().equals("ResponseBody")) {
                    annotationsIterator.remove();
                    break;
                }
            }
        }
    }

    private void removeControllerAnnotationFromClass() {
        ListIterator<AnnotationExpr> annotationsIterator = classToRemoveAnnotationFrom.getAnnotations().listIterator();
        while (annotationsIterator.hasNext()) {
            AnnotationExpr annotation = annotationsIterator.next();
            if (annotation.getName().getName().equals("Controller")) {
                annotationsIterator.remove();
                return;
            }
        }
    }

    private static class EligibilityResult {

        private boolean eligible;

        public EligibilityResult() {
            eligible = true;
        }

        public boolean isEligible() {
            return eligible;
        }

        public void setEligible(boolean eligible) {
            this.eligible = eligible;
        }
    }

    private class DetermineEligibilityVisitor extends VoidVisitorAdapter<EligibilityResult> {

        private boolean foundTopClass = false;

        @Override
        public void visit(ClassOrInterfaceDeclaration typeDeclaration, EligibilityResult result) {
            if (!foundTopClass && (typeDeclaration.getModifiers() & ModifierSet.PUBLIC) != 0x00) {
                List<AnnotationExpr> annotations = typeDeclaration.getAnnotations();
                boolean classHasControllerAnnotation = annotations.stream().filter(annotation -> annotation.getName().getName().equals("Controller")).findAny().isPresent();
                if (!classHasControllerAnnotation) {
                    result.setEligible(false);
                    return;
                } else {
                    foundTopClass = true;
                    classToRemoveAnnotationFrom = typeDeclaration;
                }
            }
            super.visit(typeDeclaration, result);
        }

        @Override
        public void visit(MethodDeclaration method, EligibilityResult result) {
            List<AnnotationExpr> annotations = method.getAnnotations();
            boolean methodHasResponseBodyAnnotation = annotations.stream().filter(annotation -> annotation.getName().getName().equals("ResponseBody")).findAny().isPresent();
            boolean methodHasRequestMappingAnnotation = annotations.stream().filter(annotation -> annotation.getName().getName().equals("RequestMapping")).findAny().isPresent();
            if (!methodHasResponseBodyAnnotation && methodHasRequestMappingAnnotation) {
                result.setEligible(false);
            } else {
                methodsToRemoveAnnotationFrom.add(method);
                super.visit(method, result);
            }
        }
    }

}
