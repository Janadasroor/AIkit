# VexDB - Custom Vector Database for Android

**VexDB** (Vector Database) is a custom SQLite-based vector database implementation built specifically for Android applications using Room persistence library. It provides efficient storage and cosine similarity search for AI embeddings with full metadata support.

## 🚀 **What is VexDB?**

VexDB is a lightweight, Android-optimized vector database that solves the problem of similarity search for AI embeddings on mobile devices. Unlike external vector databases like Qdrant, VexDB runs entirely on-device using SQLite, making it perfect for offline AI applications.

### **Key Features**
- ✅ **Cosine Similarity Search** - Find similar vectors using mathematical similarity
- ✅ **Room Integration** - Uses Android's official persistence library
- ✅ **Metadata Support** - Rich tagging, descriptions, and filtering
- ✅ **Batch Operations** - Efficient bulk vector storage and retrieval
- ✅ **Type Safety** - Full Kotlin type safety with Room
- ✅ **Android Optimized** - Designed for mobile constraints and performance

## 🏗️ **Architecture**

### **Core Components**

1. **VexDatabase** - Room database class
2. **VectorEntity** - Room entity for vector storage
3. **VectorDao** - Data Access Object for vector operations
4. **VexRepository** - Business logic layer with similarity search
5. **Type Converters** - Custom converters for complex types

### **Data Flow**
```
Gallery App → QdrantDatabaseManager → VexRepository → VectorDao → Room Database
```

## 📊 **Technical Specifications**

### **Vector Storage**
- **Format**: JSON arrays stored as strings for flexibility
- **Dimensions**: Configurable vector dimensions (512 for CLIP, variable for others)
- **Indexing**: SQLite FTS for metadata, custom similarity search

### **Similarity Search**
- **Algorithm**: Cosine similarity (cosine of angle between vectors)
- **Formula**: `similarity = (A • B) / (|A| × |B|)`
- **Range**: -1 to 1 (higher values = more similar)
- **Performance**: Optimized for mobile devices

### **Supported Operations**
- ✅ Store single vectors with metadata
- ✅ Batch vector storage
- ✅ Cosine similarity search with filtering
- ✅ Metadata-based filtering (tags, types, dates)
- ✅ Collection management
- ✅ Statistics and analytics

## 🔧 **Implementation Details**

### **VectorEntity Structure**
```kotlin
@Entity(tableName = "vectors")
data class VectorEntity(
    @PrimaryKey val id: String,
    val vectorData: String,        // JSON float array
    val dimensions: Int,           // Vector dimensions
    val vectorType: VectorType,    // IMAGE, TEXT, MULTIMODAL
    val modelType: ModelType,      // CLIP_IMAGE, CLIP_TEXT, VIT
    val source: String?,           // Origin of the vector
    val description: String?,      // Human-readable description
    val tagsJson: String,          // JSON string array of tags
    val createdAt: Long,           // Creation timestamp
    val updatedAt: Long,           // Last update timestamp
    val confidence: Float?,        // Model confidence score
    val collectionName: String     // Logical grouping
)
```

### **Similarity Search Algorithm**
```kotlin
fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
    var dotProduct = 0f
    var normA = 0f
    var normB = 0f

    for (i in a.indices) {
        dotProduct += a[i] * b[i]
        normA += a[i] * a[i]
        normB += b[i] * b[i]
    }

    val magnitude = sqrt(normA) * sqrt(normB)
    return if (magnitude > 0f) dotProduct / magnitude else 0f
}
```

## 🚀 **Usage Example**

### **Store Vectors**
```kotlin
val vectorData = VectorData(
    id = "image_001",
    vector = clipEmbedding, // FloatArray of 512 dimensions
    payload = VectorPayload(
        type = VectorType.IMAGE,
        model = ModelType.CLIP_IMAGE,
        description = "Beach sunset photo",
        tags = listOf("nature", "sunset", "beach"),
        source = "gallery"
    )
)

val result = vexRepository.storeVector(vectorData)
```

### **Search Similar Vectors**
```kotlin
val queryEmbedding = clipTextEncoder.getEmbedding("beach sunset")
val searchParams = SearchParameters(
    limit = 10,
    scoreThreshold = 0.7f,
    vectorType = VectorType.IMAGE
)

val results = vexRepository.searchSimilar(queryEmbedding, searchParams)
results.forEach { result ->
    println("Found similar image: ${result.vectorData.id}, score: ${result.score}")
}
```

### **Batch Operations**
```kotlin
val vectors = listOf(vectorData1, vectorData2, vectorData3)
val batchResult = vexRepository.storeVectorsBatch(vectors)
println("Stored ${batchResult.successful} vectors successfully")
```

## 📱 **Integration with Gallery App**

VexDB is seamlessly integrated with the AI Gallery app:

1. **Image Processing** → Extract embeddings with CLIP/ViT models
2. **Vector Storage** → Store in VexDB with metadata
3. **Text Search** → Convert text queries to embeddings
4. **Similarity Matching** → Find visually similar images
5. **Results Display** → Show ranked results with similarity scores

### **Gallery App Flow**
```
Select Folder → Process Images → Store Vectors → Search by Text → Display Results
```

## 🔍 **Search Capabilities**

### **Text-to-Image Search**
- Input: Natural language text ("sunset beach landscape")
- Process: Convert text to CLIP embedding
- Search: Find images with similar embeddings
- Results: Ranked by cosine similarity

### **Image-to-Image Search**
- Input: Query image
- Process: Extract embedding from query image
- Search: Find visually similar images
- Results: Reverse image search results

### **Advanced Filtering**
- Filter by vector type (image, text, multimodal)
- Filter by model type (CLIP, ViT)
- Filter by tags and metadata
- Date range filtering
- Similarity threshold filtering

## 📈 **Performance Characteristics**

### **Storage Efficiency**
- Vectors stored as optimized JSON strings
- Metadata indexed for fast queries
- Automatic SQLite optimization

### **Search Performance**
- Cosine similarity calculated in Kotlin (fast on modern devices)
- Batch processing for multiple queries
- Memory-efficient streaming for large result sets

### **Android Optimization**
- Room's compile-time query verification
- SQLite's built-in optimization
- Background thread execution
- Memory-mapped database access

## 🛠️ **Development & Testing**

### **Database Inspection**
```bash
# Access database file
adb shell
run-as com.jnd.aikit
cd databases
sqlite3 vexdb_vectors.db

# Inspect vectors table
.schema vectors
SELECT COUNT(*) FROM vectors;
SELECT collectionName, COUNT(*) FROM vectors GROUP BY collectionName;
```

### **Performance Monitoring**
- Use Android Profiler for database operations
- Monitor memory usage during batch operations
- Track search query performance

### **Testing Strategy**
- Unit tests for similarity calculations
- Integration tests for Room operations
- UI tests for gallery search functionality

## 🔄 **Migration & Compatibility**

### **From Mock Implementation**
VexDB replaces the previous mock implementation with full persistence:
- Mock data → SQLite storage
- In-memory search → Cosine similarity on disk
- Temporary results → Persistent vector database

### **Version Compatibility**
- Room handles schema migrations automatically
- Vector format remains backward compatible
- Metadata structure extensible

## 🎯 **Why VexDB vs Other Solutions**

| Feature | VexDB | Qdrant | Pinecone | Weaviate |
|---------|-------|--------|----------|----------|
| **Platform** | Android | Cloud/Server | Cloud | Cloud/Server |
| **Offline** | ✅ Yes | ❌ No | ❌ No | ❌ No |
| **Setup** | Automatic | Complex | API Key | Complex |
| **Cost** | Free | Paid | Paid | Paid |
| **Mobile** | ✅ Optimized | ❌ Not designed | ❌ Not designed | ❌ Not designed |
| **Privacy** | ✅ On-device | ❌ Cloud | ❌ Cloud | ❌ Cloud |

## 🚀 **Future Enhancements**

### **Performance Optimizations**
- Native cosine similarity (C++/JNI)
- Vector quantization for storage efficiency
- Approximate nearest neighbor (ANN) algorithms
- GPU acceleration for similarity calculations

### **Advanced Features**
- Multi-modal search (text + image queries)
- Semantic filtering and faceted search
- Vector clustering and categorization
- Real-time vector updates and streaming

### **Integration Features**
- Export/import vector collections
- Backup and restore functionality
- Cross-device synchronization
- API endpoints for external access

## 📚 **API Reference**

### **VexRepository Methods**
- `initialize()` - Initialize database
- `storeVector(vectorData)` - Store single vector
- `storeVectorsBatch(vectors)` - Batch storage
- `searchSimilar(queryVector, params)` - Similarity search
- `getVector(collection, id)` - Retrieve vector
- `deleteVector(collection, id)` - Delete vector
- `getCollectionStats(collection)` - Collection statistics
- `listCollections()` - List all collections

### **Search Parameters**
```kotlin
data class SearchParameters(
    val limit: Int = 10,
    val scoreThreshold: Float? = null,
    val vectorType: VectorType? = null,
    val modelType: ModelType? = null
)
```

## 🎉 **Conclusion**

VexDB represents a breakthrough in mobile AI applications by bringing professional vector database capabilities directly to Android devices. With cosine similarity search, rich metadata support, and seamless Room integration, VexDB enables sophisticated AI-powered features while maintaining the privacy and performance benefits of on-device processing.

**The future of mobile AI is local, and VexDB makes it possible!** 🚀
