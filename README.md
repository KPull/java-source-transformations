# java-source-transformations
Execute the Rest Controller Transformation by downloading the source code and then execute the following using Maven:

    mvn compile exec:java < sourceFileToTransform.java > sourceFileToWrite.java
    
The replacement will transform any classes marked with `@Controller` to `@RestController` if possible. The transformation will not be performed if there
exists a method declaration that has a `@RequestMapping` annotation but does not have a `@ResponseBody` annotation.