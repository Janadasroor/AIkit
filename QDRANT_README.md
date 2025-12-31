# Qdrant Vector Database Integration

This Android app now includes a professional Qdrant vector database integration for storing and retrieving AI model embeddings (CLIP and ViT).

## Features

### ✅ Vector Storage
- Store CLIP image and text embeddings
- Store ViT image embeddings
- Rich metadata support (tags, descriptions, timestamps)
- Automatic collection management

### ✅ Similarity Search
- Text-to-image search
- Image-to-image search (reverse image search)
- Configurable similarity thresholds
- Filtered search by model type, tags, and time ranges

### ✅ Batch Operations
- Efficient bulk vector storage
- Batch search operations
- Error handling and reporting

### ✅ Professional Features
- Connection management and health checks
- Configuration persistence
- Comprehensive error handling with Result types
- Logging and monitoring
- Collection statistics

## Setup

### 1. Qdrant Server
You'll need a running Qdrant server. Options:

**Docker (Recommended for development):**
```bash
docker run -p 6333:6333 -p 6334:6334 qdrant/qdrant
```

**Local installation:**
```bash
# Install Qdrant
curl -L https://github.com/qdrant/qdrant/releases/download/v1.7.4/qdrant-x86_64-unknown-linux-gnu.tar.gz | tar xz
cd qdrant
./qdrant
```

**Cloud:** Qdrant Cloud (https://cloud.qdrant.io)

### 2. Configuration
Update the Qdrant connection settings in `EmbeddingViewModel.kt`:

```kotlin
qdrantManager.initialize(
    QdrantDatabaseManager.Config(
        host = "your-qdrant-host", // localhost for local, URL for cloud
        port = 6334, // 6333 for HTTP, 6334 for gRPC
        enableLogging = true // Set to false in production
    )
)
```

## Usage

### Process Images
```kotlin
// Store image with metadata
viewModel.processImage(
    bitmap = imageBitmap,
    description = "A beautiful sunset",
    tags = listOf("nature", "sunset", "landscape")
)
```

### Process Text
```kotlin
// Store text content
viewModel.processText(
    text = "Machine learning is transforming technology",
    description = "Article about AI",
    tags = listOf("AI", "ML", "technology")
)
```

### Search Similar Content
```kotlin
// Text-based search
viewModel.searchSimilar(
    query = "nature landscape",
    limit = 10,
    searchImages = true,
    searchText = false,
    minScore = 0.7f
)

// Image-based search (reverse image search)
viewModel.searchSimilarByImage(
    bitmap = queryImage,
    limit = 5,
    minScore = 0.8f
)
```

### Database Management
```kotlin
// Get database statistics
viewModel.getDatabaseStats()
```

## Architecture

### Core Components

1. **QdrantDatabaseManager**: Main database interface
   - Connection management
   - CRUD operations
   - Batch processing
   - Error handling

2. **VectorData**: Data models
   - Vector embeddings
   - Rich metadata
   - Search results

3. **QdrantConfigManager**: Configuration persistence
   - Settings storage
   - Connection status tracking

4. **EmbeddingViewModel**: Application interface
   - Model inference
   - Database operations
   - UI integration

### Collections

The system automatically creates and manages these collections:
- `images`: CLIP and ViT image embeddings
- `text`: CLIP text embeddings
- `multimodal`: Combined embeddings (future use)

### Vector Dimensions

- CLIP embeddings: 512 dimensions
- ViT embeddings: Variable (depends on model)

## API Reference

### QdrantDatabaseManager

#### Core Methods
- `initialize(config)`: Initialize database connection
- `storeVector(vectorData)`: Store single vector
- `storeVectorsBatch(vectors)`: Store multiple vectors
- `searchSimilar(queryVector, parameters)`: Similarity search
- `getVector(collection, id)`: Retrieve vector by ID
- `deleteVector(collection, id)`: Delete vector

#### Management Methods
- `createCollection(name, dimension)`: Create new collection
- `deleteCollection(name)`: Delete collection
- `listCollections()`: List all collections
- `getCollectionStats(name)`: Get collection statistics

### EmbeddingViewModel

#### Processing Methods
- `processImage(bitmap, description, tags)`: Process and store image
- `processText(text, description, tags)`: Process and store text
- `searchSimilar(query, limit, searchImages, searchText, minScore)`: Text search
- `searchSimilarByImage(bitmap, limit, minScore)`: Image search

## Error Handling

The integration uses Kotlin's `Result` type for error handling:

```kotlin
val result = qdrantManager.storeVector(vectorData)
result.onSuccess {
    // Handle success
}.onFailure { exception ->
    // Handle error (QdrantException)
    Log.e("Error", "Failed to store vector", exception)
}
```

## Performance Optimization

### Tips for Production
1. **Connection Pooling**: Reuse connections
2. **Batch Operations**: Use `storeVectorsBatch()` for multiple vectors
3. **Indexing**: Qdrant automatically handles HNSW indexing
4. **Memory Management**: Monitor vector sizes and collection growth
5. **Network**: Use gRPC (port 6334) for better performance than HTTP

### Monitoring
- Enable logging in development
- Monitor collection sizes
- Track search performance
- Check connection health

## Troubleshooting

### Common Issues

1. **Connection Failed**
   - Verify Qdrant server is running
   - Check host/port configuration
   - Ensure network connectivity

2. **Collection Not Found**
   - Collections are auto-created on first use
   - Check server logs for creation errors

3. **Search Returns No Results**
   - Verify vectors are stored successfully
   - Check similarity thresholds
   - Ensure correct collection is being searched

4. **Out of Memory**
   - Reduce batch sizes
   - Monitor vector dimensions
   - Check device memory usage

### Logs
Enable detailed logging in `QdrantDatabaseManager.Config` to debug issues.

## Future Enhancements

- Hybrid search (combining multiple embedding types)
- Real-time vector updates
- Advanced filtering options
- Performance metrics dashboard
- Backup and restore functionality
- Distributed deployment support
