# Haya

HAYA is an offline-first Android app for running GGUF language models on-device.

This repository is a college-project adaptation of an open-source Android local-LLM app. The current codebase is focused on:

- importing a `.gguf` model into app storage
- loading the model through the bundled `redhat1406` JNI layer
- chatting with the model through a simple Compose UI
- keeping `llama.cpp` as a Git submodule

## Current State

Right now the active app flow is a simplified single-screen experience:

1. pick a GGUF model file
2. copy it into the app's internal storage
3. load it locally on-device
4. send prompts and stream responses back into the chat UI

Qwen GGUF models are already working in this setup.

## Project Structure

- `app/`: Android application module
- `redhat1406/`: JNI/Kotlin wrapper around local LLM inference
- `hf-model-hub-api/`: Hugging Face model helper module
- `llama.cpp/`: upstream Git submodule used by the native layer
- `docs/`: integration and release notes

## Clone With Submodules

Because `llama.cpp` is included as a submodule, clone the project with:

```bash
git clone --recurse-submodules https://github.com/Audi14-2005/JUNE_MD.git
```

If you already cloned it without submodules:

```bash
git submodule update --init --recursive
```

## Build

Open the project in Android Studio and sync Gradle, or use:

```bash
./gradlew.bat :app:assembleDebug
```

The app targets:

- `minSdk 26`
- `targetSdk 35`
- Kotlin + Jetpack Compose UI

## Notes

- Inference is designed to run locally on the device.
- The project currently contains a simplified UI path compared with the larger original app architecture.
- `llama.cpp` remains an external upstream submodule and should be kept initialized when cloning or building.

Author: Monic Auditya A
