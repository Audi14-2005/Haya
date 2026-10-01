# Integrating the `redhat1406` module in other projects for using LLMs

The `redhat1406` Gradle module contains the JNI bindings to llama.cpp that are used by SmolChat. This guide will cover the steps on how to integrate the LLM inference capabilities of SmolChat in other applications or Android projects.

#### Building the AAR

Open this repository in Android Studio and make sure it builds successfully. In the root directory, use the following command,

```
gradlew :redhat1406:assemble
```

to generate an Android Archive (`.aar` file) for the `redhat1406` module. The generated AARs can be found in the `redhat1406/build/outputs` directory.

#### Including the AAR in an Android Studio Project

In an Android project, copy the AAR file to the `app/libs` directory (create the `libs` directory if it does not exist). Next, in `app/build.gradle.kts` `dependencies` block, add the following line,

```kotlin
dependencies {
    // ...
    implementation(files("libs/redhat1406.aar"))
    // ...
}
```

and sync the project. The classes `RedHat1406` and `GGUFReader` should now be available for use in the project.

#### Using the `RedHat1406` class

1. Instantiate the `RedHat1406` class for a single conversation, like `val redhat1406 = RedHat1406()`.
2. Initialize the inference pipeline by calling `redhat1406.create` with chat parameters and path to the GGUF model.
3. Use `addUserMessage`, `addAssistantMessage` and `addSystemPrompt` methods to add messages with different roles to the conversation. `addSystemPrompt` should only be called once at the beginning of the conversation.
4. Use `getResponse` to get the response from the LLM as a stream of tokens i.e. `Flow<String>`.
5. Use `getResponseGenerationSpeed` to get the rate at which the LLM is generating the response and `getContextLengthUsed` to get the number of tokens consumed by the context window.
6. Call `redhat1406.close` to release resources taken by the native code.
