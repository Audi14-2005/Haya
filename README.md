# HAYA: Mobile LLM Inference and Intelligent Selection - Energy Awareness

[![Project Team](https://img.shields.io/badge/Project%20Team-T109-blue.svg)](#project-metadata--team)
[![Affiliation](https://img.shields.io/badge/Institution-Rajalakshmi%20Engineering%20College-red.svg)](#project-metadata--team)
[![Platform](https://img.shields.io/badge/Platform-Android%20%7C%20ARM64-green.svg)](#high-level-source-code-overview)
[![Engine](https://img.shields.io/badge/Inference-llama.cpp%20%28GGUF%29-orange.svg)](#high-level-source-code-overview)
[![Documentation](https://img.shields.io/badge/Documentation-Phase%20I%20Report%20%2892%20Pages%29-purple.svg)](#2-comprehensive-phase-i-project-report-t109_report)

---

## Overview

**HAYA** is an offline-first, on-device Large Language Model (LLM / SLM) inference framework and intelligent selection system designed for Android smartphones and local Wi-Fi mesh environments. 

Running modern generative language models entirely on mobile devices eliminates cloud dependencies and preserves absolute user data privacy. However, mobile devices face acute constraints in compute capacity, battery endurance, dynamic thermal throttling, and memory limits. Naively executing LLMs or rotating requests between phones without considering hardware state leads to severe latency spikes, thermal degradation, and excessive power drain. Furthermore, naively switching between models introduces heavy overhead from disk I/O model swapping and KV-cache history token reconstruction.

To resolve these challenges, project **T109** introduces:
1. **Live Compute Index (LCI)**: A real-time telemetry scoring algorithm evaluating mobile candidates across queue backlog, execution latency, thermal throttling, and battery status.
2. **Event-Based Mesh Routing**: Dynamic host selection across an offline Wi-Fi mesh that routes queries to optimal host phones and re-evaluates hosting upon explicit hardware events (thermal warnings, battery depletion, disconnects).
3. **Swap-Cost-Aware Routing**: A hysteresis-guided model switching policy that amortizes model loading and history reconstruction overhead, switching models only when the quality gain demonstrably outweighs the transition cost.

---

## Repository Folder Structure

The repository is organized into dedicated directories separating academic publications, the comprehensive phase report, video demonstrations, and the technical implementation:

```text
Haya/
├── README.md                                    # Root project documentation and overview
│
├── T109_Conference_paper/                       # Academic conference manuscripts & submission proofs
│   ├── T109_1st_Conference_paper.pdf            # Paper 1: LCI & Event-Based Routing for Offline Group Chat
│   ├── T109_1st_Conference_submission_proof.pdf # Submission confirmation proof for Paper 1 (IEEE AIEI 2027)
│   ├── T109_1st_Conference_paper_plagiarism report.pdf # Turnitin plagiarism report for Paper 1 (3% similarity)
│   ├── T109_2nd_Conference_paper.pdf            # Paper 2: Haya Swap-Cost-Aware On-Device Model Routing
│   ├── T109_2nd_Conference_submission_proof.pdf # Submission confirmation proof for Paper 2 (IEEE / ICOAC)
│   └── T109_2nd_Conference_Paper_plagiarism report.pdf # Turnitin plagiarism report for Paper 2 (2% similarity)
│
├── T109_Report/                                 # Comprehensive Phase I engineering report
│   └── T109_Report_Plag_Submission_Papers_v4.pdf # Complete 92-page project report with appendices
│
├── T109_Video_link/                             # Video demonstration links
│   └── T109_link                                # Google Drive link to live system demonstration
│
└── T109_Src_code/                               # Application source code and developer documentation
    ├── app/                                     # Android client module (Jetpack Compose UI)
    ├── redhat1406/                              # JNI C++ / Kotlin native inference module
    ├── llama.cpp/                               # Upstream llama.cpp inference submodule
    ├── hf-model-hub-api/                        # Hugging Face model integration module
    ├── docs/                                    # Technical architecture & integration documentation
    │   ├── build_arm_flags.md                   # ARM64/ARMv7 CPU extension build optimizations
    │   ├── integrating_redhat1406.md            # Guide to integrating the inference AAR module
    │   ├── release-checklist.md                 # Production release and tagging procedure
    │   └── update_llamacpp_submodule.md         # Submodule sync and maintenance workflow
    ├── metadata/                                # App store listing metadata, screenshots, and descriptions
    ├── resources/                               # Visual assets and application icons
    ├── build.gradle.kts                         # Root Gradle build script
    └── settings.gradle.kts                      # Gradle project settings
```

---

## Detailed Documentation Guide

The documentation in this repository spans formal academic research papers, an exhaustive Phase I project report, technical integration guides, and live demonstration records.

### 1. Conference Research Papers (`T109_Conference_paper/`)

This directory contains two research papers produced during the project, detailing the mathematical formulation, algorithmic state machines, and experimental evaluations:

#### Paper 1: *A Live Computation Index and Event-Based Routing Framework for Offline Smartphone Group AI Chat*
- **File**: [`T109_Conference_paper/T109_1st_Conference_paper.pdf`](T109_Conference_paper/T109_1st_Conference_paper.pdf)
- **Conference Submission**: 2nd IEEE International Conference on AI Engineering and Innovation (AIEI 2027)
- **Track**: Track 7: AI for Education, Technology Management and Engineering
- **Paper ID**: `1532`
- **Core Focus**:
  - Focuses on hosting multi-user AI chats completely offline across a local Wi-Fi mesh network of smartphones without cloud infrastructure.
  - Formulates the **Live Computation Index (LCI)**, a composite metric integrating queue backlog, service time, and real-time thermal slowdown factors.
  - Implements an **Event-Based Routing State Machine** that reacts flexibly to hardware threshold signals (battery limits, thermal throttling, connection dropouts) rather than continuously polling, drastically reducing scheduling overhead.
- **Verification Documents**:
  - **Submission Proof**: [`T109_Conference_paper/T109_1st_Conference_submission_proof.pdf`](T109_Conference_paper/T109_1st_Conference_submission_proof.pdf)
  - **Plagiarism Report**: [`T109_Conference_paper/T109_1st_Conference_paper_plagiarism report.pdf`](T109_Conference_paper/T109_1st_Conference_paper_plagiarism report.pdf) — verified **3% similarity index** via Turnitin Originality Report.

#### Paper 2: *Haya: Live-Compute-Index-Guided, Swap-Cost-Aware Routing for On-Device LLMs*
- **File**: [`T109_Conference_paper/T109_2nd_Conference_paper.pdf`](T109_Conference_paper/T109_2nd_Conference_paper.pdf)
- **Conference Submission**: 13th International Conference on Advanced Computing (ICOAC 2026) / 2027 IEEE MP Section Conference
- **Track**: Track 1: Artificial Intelligence and Machine Learning
- **Paper ID**: `330` / `156`
- **Core Focus**:
  - Addresses on-device model routing between multiple resident small language models (e.g., Qwen series in GGUF format).
  - Identifies that naively switching models based purely on task complexity creates major latency penalties because **reconstructing 1,024 history tokens consumes up to 85% of the total handoff time** (approx. 29.12 seconds on mobile hardware).
  - Proposes a **hysteresis-based swap-cost policy** that only switches models when expected inference gains exceed the amortized model loading and KV-cache rebuild overhead.
  - **Key Empirical Results**: Compared to complexity-only routing, Haya reduced latency by **29.6%** (Realme GT 6T) and **32.1%** (Realme 11 Pro+), lowered energy consumption by **17.2%** and **20.7%**, and reduced total model switches by **~70%** (from 41 to 11 switches).
- **Verification Documents**:
  - **Submission Proof**: [`T109_Conference_paper/T109_2nd_Conference_submission_proof.pdf`](T109_Conference_paper/T109_2nd_Conference_submission_proof.pdf)
  - **Plagiarism Report**: [`T109_Conference_paper/T109_2nd_Conference_Paper_plagiarism report.pdf`](T109_Conference_paper/T109_2nd_Conference_Paper_plagiarism report.pdf) — verified **2% overall similarity** via Turnitin Originality Report.

---

### 2. Comprehensive Phase I Project Report (`T109_Report/`)

- **File**: [`T109_Report/T109_Report_Plag_Submission_Papers_v4.pdf`](T109_Report/T109_Report_Plag_Submission_Papers_v4.pdf)
- **Document Details**: 92-page official Phase I engineering report submitted to Anna University & Rajalakshmi Engineering College.
- **Title**: *Mobile LLM Inference and Intelligent Selection - Energy Awareness*

The report provides comprehensive documentation of the project across 6 core chapters and supporting appendices:

| Chapter | Title | Summary of Content |
| :--- | :--- | :--- |
| **Chapter 1** | **Introduction** | Background on on-device generative AI, motivation for offline smartphone group chat, project objectives, limitations of cloud-tethered LLMs, and project scope. |
| **Chapter 2** | **Literature Survey** | Detailed comparative analysis of modern edge-inference architectures (e.g., *EdgeShard*, *LinguaLinked*, *Confidant*, *HybridInfer*, *EnerInfer*), highlighting gaps in swap-cost and thermal awareness. |
| **Chapter 3** | **Proposed System Architecture** | Formal architectural design, smartphone roles across the local Wi-Fi mesh, hardware/software specifications, security considerations, activity diagrams, and data flow diagrams (DFD). |
| **Chapter 4** | **Project Description & Modules** | Deep dive into the 5 core modules: Mesh Communication Module, LCI Computation Module, Event-Based Routing Module, Host Inference Module, and Telemetry Dashboard. |
| **Chapter 5** | **Implementation & Evaluation** | Experimental testbed results across 5 heterogeneous smartphones (SoC, thermal curves, battery degradation, Time To First Token [TTFT], prefill throughput, and tail latency). |
| **Chapter 6** | **Conclusion & Future Work** | Summary of Phase I milestones, energy/latency tradeoffs, and roadmap for Phase II multi-device collaborative model splitting. |
| **Appendices** | **Publications & Reports** | Includes full conference paper submissions, CMT submission acknowledgments, and certified Turnitin plagiarism originality reports. |

---

### 3. Technical Integration & Development Guides (`T109_Src_code/docs/`)

The [`T109_Src_code/docs/`](T109_Src_code/docs/) folder contains engineering notes and technical guides for compiling, optimizing, and integrating the underlying inference engine:

1. **[`build_arm_flags.md`](T109_Src_code/docs/build_arm_flags.md)**:
   - Explains compilation of `llama.cpp` using ARM-specific CPU feature modifiers to maximize on-device inference speed.
   - Covers 64-bit ARMv8/ARMv9 extensions: `fp16` (half-precision floating point), `dotprod` (vector dot product), `i8mm` (8-bit integer matrix multiply), and `sve` (scalable vector extensions).
   - Covers 32-bit ARMv7 flags: `-mfpu=neon-vfpv4` and `-mfloat-abi=softfp`.
   - Explains how multiple shared libraries (`.so`) are compiled into a unified target in `CMakeLists.txt` and dynamically loaded at runtime based on device CPU capabilities.

2. **[`integrating_redhat1406.md`](T109_Src_code/docs/integrating_redhat1406.md)**:
   - Instructions on compiling the `redhat1406` module into an Android Archive (`.aar`) file via `./gradlew :redhat1406:assemble`.
   - Explains how third-party Android projects can integrate the resulting `.aar` library into their `app/libs` directory.
   - Provides Kotlin code examples using `RedHat1406` and `GGUFReader` classes to stream responses via Kotlin Coroutines `Flow<String>`, monitor token generation speed, and track context window usage.

3. **[`release-checklist.md`](T109_Src_code/docs/release-checklist.md)**:
   - Step-by-step operating checklist for syncing upstream `llama.cpp` commits.
   - Details changelog updates, version code/name increments in `app/build.gradle.kts`, and GitHub tagging procedures for CI/CD builds.

4. **[`update_llamacpp_submodule.md`](T109_Src_code/docs/update_llamacpp_submodule.md)**:
   - Instructions for maintaining and updating the `llama.cpp` Git submodule tracking the upstream master branch.

---

### 4. Live Demonstration Video (`T109_Video_link/`)

- **File**: [`T109_Video_link/T109_link`](T109_Video_link/T109_link)
- **Drive Link**: [Google Drive Demonstration Video](https://drive.google.com/file/d/1410PWjfDo9qVhW6OKPo-D3bzLqi0AB14/view?usp=drive_link)
- **Content**: Demonstrates the real-time application in operation:
  - Local GGUF model import and internal storage initialization.
  - On-device prompt processing and streaming token generation.
  - User interface interactions, task creation, and inference parameter tuning.

---

## High-Level Source Code Overview

> *Note: Detailed internal source code implementation details are kept concise here in accordance with documentation guidelines.*

The source code directory [`T109_Src_code/`](T109_Src_code/) contains the complete Android application and native JNI inference pipeline:

```text
T109_Src_code/
├── app/                  # Main Android application (Jetpack Compose UI, ViewModels, model picker)
├── redhat1406/           # JNI bridge (C++ and Kotlin) wrapping llama.cpp for Android hardware
├── llama.cpp/            # Upstream Git submodule providing core C++ tensor operations and LLM inference
├── hf-model-hub-api/     # Utility module for Hugging Face model metadata and hub access
└── docs/                 # Technical documentation and integration guides
```

### Module Roles
- **`app`**: Provides the modern Jetpack Compose mobile user interface, handling local file picking of `.gguf` models, conversation thread persistence, and streaming token rendering.
- **`redhat1406`**: The core native abstraction layer. Exposes clean Kotlin APIs (`RedHat1406`, `GGUFReader`) backed by optimized C++ JNI implementations that interface directly with `llama.cpp`.
- **`llama.cpp`**: Included as a Git submodule, executing quantized GGUF neural network operations on the device CPU/GPU.
- **`hf-model-hub-api`**: Facilitates searching and downloading compatible GGUF models directly from Hugging Face.

### Quick Build Instructions
1. Ensure the Git submodule is initialized:
   ```bash
   git submodule update --init --recursive
   ```
2. Build the Android application using Gradle:
   ```bash
   cd T109_Src_code
   ./gradlew.bat :app:assembleDebug
   ```
3. Target environment:
   - **Minimum SDK**: `26` (Android 8.0)
   - **Target SDK**: `35` (Android 15)
   - **Toolchain**: Android NDK + CMake + Kotlin + Jetpack Compose

---

## Key Experimental Results

The project was evaluated using physical smartphones (including Realme GT 6T and Realme 11 Pro+) running Q4_0 quantized language models:

| Metric | Complexity-Only (Baseline) | Haya (Swap-Cost & LCI Aware) | Improvement |
| :--- | :--- | :--- | :--- |
| **Inference Latency (GT 6T)** | Baseline | Reduced by 29.6% | **29.6% faster** |
| **Inference Latency (11 Pro+)** | Baseline | Reduced by 32.1% | **32.1% faster** |
| **Energy Consumption (GT 6T)** | Baseline | Reduced by 17.2% | **17.2% lower power** |
| **Energy Consumption (11 Pro+)** | Baseline | Reduced by 20.7% | **20.7% lower power** |
| **Model Switches (200 queries)** | 41 - 44 switches | 11 - 13 switches | **~70% fewer swaps** |
| **Quality Score (GT 6T)** | 0.724 | 0.746 | **Maintained / Improved** |

---

## Project Metadata & Team

- **Project ID**: `T109`
- **Academic Year**: 2026 - 2027
- **Degree**: Bachelor of Engineering in Computer Science and Engineering
- **Institution**: Rajalakshmi Engineering College (REC), Chennai (Affiliated with Anna University)

### Authors
- **Monic Auditya A** (Roll No: 230701194)
- **Atchaya S** (Roll No: 230701045)
- **Ramya Sreevarshini B** (Roll No: 230701262)
- **Saranya M** (Roll No: 230701510)

### Project Supervisor
- **Mr. Karthikeyan N**, Assistant Professor, Department of Computer Science and Engineering, Rajalakshmi Engineering College
