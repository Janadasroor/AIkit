# AI Kit Gallery 📸🤖

An advanced **AI-powered image gallery** for Android featuring **offline CLIP and ViT models** for intelligent image search and classification. Built with Jetpack Compose and ONNX Runtime for high-performance, privacy-focused AI capabilities.

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.21-purple.svg)](https://kotlinlang.org)
[![ONNX Runtime](https://img.shields.io/badge/ONNX_Runtime-1.16.0-orange.svg)](https://onnxruntime.ai)

## ✨ Features

### 🎨 **Smart Image Gallery**
- **Beautiful Material Design 3** interface with smooth animations
- **Organized folder view** showing all your device image albums
- **Grid layout** with responsive design for all screen sizes
- **Image thumbnails** with fast loading and caching

### 🔍 **AI-Powered Search**
- **Text-to-Image Search**: Find images using natural language descriptions
- **Image-to-Image Search**: Find similar images by selecting a reference image
- **CLIP Model Integration**: Uses OpenAI's CLIP for semantic understanding
- **ViT Model Support**: Google's Vision Transformer for visual similarity
- **Real-time search** with instant results

### 🧠 **Offline AI Processing**
- **100% Offline**: No internet required for AI features
- **Privacy-First**: All processing happens on-device
- **Quantized Models**: Optimized for mobile performance
- **Multi-Model Support**: Choose between CLIP and ViT models

### 📁 **Virtual Collections**
- **Processed Images Folder**: Smart collection of AI-indexed images
- **Batch Processing**: Process entire folders or selected images
- **Progress Tracking**: Real-time processing status and statistics
- **Automatic Organization**: Images organized by processing status

### 🎛️ **Advanced Controls**
- **Memory Management**: Monitor and optimize app memory usage
- **Cache Management**: Clear search and image caches
- **Database Optimization**: Maintain vector database performance
- **Model Management**: Download and manage AI models

### 🎨 **Customization**
- **Theme Support**: Light/Dark mode with system integration
- **Beautiful Drawer Navigation**: Smooth animated navigation drawer
- **Responsive Design**: Optimized for phones and tablets
- **Customizable Settings**: Personalize your experience

## 🚀 Getting Started

### Prerequisites
- **Android Studio**: Arctic Fox or later
- **Minimum SDK**: API 24 (Android 7.0)
- **Target SDK**: API 34 (Android 14)
- **Kotlin**: 1.9.21+

### Installation

1. **Clone the repository**
   ```bash
   git clone https://github.com/JanadaSroor/AI-Kit-Gallery.git
   cd AI-Kit-Gallery
   ```

2. **Download AI Models**
   Download the ONNX models from [Hugging Face](https://huggingface.co/JanadaSroor):
   ```bash
   # Download CLIP models
   wget https://huggingface.co/JanadaSroor/clip-vit-base-patch32-onnx/resolve/main/clip_text_quantized.onnx
   wget https://huggingface.co/JanadaSroor/clip-vit-base-patch32-onnx/resolve/main/clip_vision_quantized.onnx

   # Download ViT model
   wget https://huggingface.co/JanadaSroor/vit-base-patch16-224-onnx/resolve/main/vit_base_quantized.onnx
   ```

   Place the downloaded models in `app/src/main/assets/` directory.

3. **Build and Run**
   ```bash
   ./gradlew build
   ./gradlew installDebug
   ```

## 📱 Usage Guide

### Basic Navigation
1. **Launch the app** and grant storage permissions
2. **Browse folders** to see all your image albums
3. **Tap any folder** to view images in a beautiful grid
4. **Use the drawer menu** for additional features

### AI Search Features

#### Text Search
1. Open the **Search** from the drawer menu
2. Enter a description (e.g., "beach sunset", "red sports car")
3. View results ranked by semantic similarity

#### Image Search
1. **Long-press any image** in the gallery
2. Select **"Search Similar"** from the menu
3. View visually similar images

### Processing Images
1. **Select images** by tapping checkboxes in folder view
2. **Choose "Process Selected"** or "Process All" from the top bar
3. **Monitor progress** with real-time updates
4. **View processed images** in the "Processed Images" folder

### Memory Management
1. Open **Memory Management** from the drawer
2. **Monitor system memory** usage with beautiful charts
3. **Clear caches** and optimize database
4. **View storage statistics** and processed image counts

## 🧠 AI Models

The app uses optimized ONNX models for offline AI processing:

### CLIP Models (OpenAI)
- **Text Encoder**: `clip_text_quantized.onnx` (62MB)
- **Vision Encoder**: `clip_vision_quantized.onnx` (337MB) - Not quantized due to compatibility issues
- **Use Case**: Text-to-image and image-to-text similarity
- **Architecture**: Vision Transformer + Text Transformer

### ViT Model (Google)
- **Model**: `vit_base_quantized.onnx` (84MB)
- **Use Case**: Image-to-image similarity and classification
- **Architecture**: Vision Transformer Base (patch16-224)

### Model Specifications
| Model | Size | Input | Output | Use Case |
|-------|------|-------|--------|----------|
| CLIP Text | 62MB | Text tokens | 512D embedding | Text search |
| CLIP Vision | 337MB | 224x224 RGB | 512D embedding | Image encoding |
| ViT Base | 84MB | 224x224 RGB | 768D embedding | Visual similarity |

## 🤗 Hugging Face Models

All models are available on Hugging Face:

### CLIP ViT-Base-Patch32
- **Repository**: [`JanadaSroor/clip-vit-base-patch32-onnx`](https://huggingface.co/JanadaSroor/clip-vit-base-patch32-onnx)
- **Base Model**: `openai/clip-vit-base-patch32`
- **Optimization**: INT8 quantization for mobile deployment

### ViT Base Patch16-224
- **Repository**: [`JanadaSroor/vit-base-patch16-224-onnx`](https://huggingface.co/JanadaSroor/vit-base-patch16-224-onnx)
- **Base Model**: `google/vit-base-patch16-224`
- **Optimization**: INT8 quantization for mobile performance

## 📚 Colab Example

Try the AI models with this Google Colab notebook:

[![Open In Colab](https://colab.research.google.com/assets/colab-badge.svg)](https://colab.research.google.com/github/JanadaSroor/AI-Kit-Gallery/blob/main/colab/AI_Models_Demo.ipynb)

### Colab Features
- **Model Download**: Automated download from Hugging Face
- **Inference Examples**: Text-to-image and image-to-image search
- **Performance Benchmarking**: Compare model speeds and accuracy
- **Visualization**: See embeddings and similarity scores

```python
# Quick start in Colab
from transformers import CLIPProcessor, CLIPModel
import torch

# Load models
model = CLIPModel.from_pretrained("openai/clip-vit-base-patch32")
processor = CLIPProcessor.from_pretrained("openai/clip-vit-base-patch32")

# Example inference
inputs = processor(text=["a photo of a cat"], images=[image], return_tensors="pt")
outputs = model(**inputs)
```

## 🏗️ Architecture

### App Structure
```
app/src/main/java/com/jnd/aikit/
├── ui/
│   ├── gallery/          # Gallery screens and navigation
│   │   ├── FolderSelectionScreen.kt
│   │   ├── ImageGalleryScreen.kt
│   │   ├── SearchScreen.kt
│   │   ├── MemoryManagementScreen.kt
│   │   ├── SettingsScreen.kt
│   │   └── DrawerNavigation.kt
│   └── model/            # Model management UI
├── database/             # Vector database (VexDB)
├── model/                # AI model management
├── embedding/            # CLIP and ViT encoders
└── MainActivity.kt
```

### Key Components
- **GalleryViewModel**: Manages gallery state and operations
- **EmbeddingViewModel**: Handles AI model inference
- **VexDatabase**: Custom vector database for similarity search
- **ModelManager**: Downloads and manages ONNX models

### Data Flow
1. **Image Loading** → GalleryViewModel → UI Display
2. **AI Processing** → EmbeddingViewModel → Vector Storage
3. **Search Query** → Similarity Search → Ranked Results
4. **Memory Management** → Cache/Database Optimization

## 🔧 Technical Details

### Dependencies
```kotlin
dependencies {
    // ONNX Runtime for AI inference
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.16.0")

    // Room for vector database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")

    // Jetpack Compose for UI
    implementation("androidx.compose.ui:ui:1.5.4")
    implementation("androidx.compose.material3:material3:1.1.2")

    // Image loading and processing
    implementation("io.coil-kt:coil-compose:2.5.0")
}
```

### Performance Optimizations
- **Model Quantization**: INT8 quantization reduces model size by 75%
- **Lazy Loading**: Images and search results loaded on-demand
- **Memory Pooling**: Reusable memory buffers for inference
- **Background Processing**: AI operations run on separate threads

### Storage Usage
- **Models**: ~483MB total (62MB CLIP Text + 337MB CLIP Vision + 84MB ViT)
- **Database**: ~50-200MB depending on processed images
- **Cache**: ~20-100MB temporary image thumbnails
- **Total**: ~250-500MB with full functionality

## 📊 Privacy & Security

### Privacy-First Design
- **Zero Data Transmission**: All processing happens offline
- **No Cloud Dependencies**: Completely self-contained
- **Local Storage Only**: Images and embeddings stored locally
- **No Telemetry**: No data collection or analytics

### Security Features
- **Permission-Based Access**: Storage access only when granted
- **Secure Model Storage**: Models stored in app-private directory
- **Encrypted Database**: Vector database with SQLCipher support
- **Memory Sanitization**: Sensitive data cleared from memory

## 🤝 Contributing

We welcome contributions! Please see our [Contributing Guide](CONTRIBUTING.md) for details.

### Development Setup
1. Fork the repository
2. Create a feature branch: `git checkout -b feature/amazing-feature`
3. Make your changes and add tests
4. Commit: `git commit -m 'Add amazing feature'`
5. Push: `git push origin feature/amazing-feature`
6. Open a Pull Request

### Areas for Contribution
- **Model Optimization**: Improve ONNX model performance
- **UI Enhancements**: Better animations and user experience
- **New AI Features**: Additional search modalities
- **Database Improvements**: Better vector indexing
- **Cross-Platform**: iOS version development

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- **OpenAI** for the CLIP model architecture
- **Google** for the Vision Transformer models
- **ONNX** for the model format and runtime
- **Android Jetpack** for the development framework


## 🔄 Updates

### Version 1.0.0
- Initial release with CLIP and ViT support
- Offline AI processing capabilities
- Beautiful Material Design 3 interface
- Memory management and optimization features

---

**Made with ❤️ by [JanadaSroor](https://github.com/JanadaSroor)**

*Transform your Android gallery into an AI-powered search experience!*
