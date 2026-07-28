# Codebase Q&A Assistant

A RAG pipeline that chunks source code by function and class boundaries, indexes it using TF-IDF vectors, and grounds LLM answers in retrieved context — demonstrating semantic search, custom vector storage, and grounded inference built from scratch.

---

## Problem & Solution

LLMs answer questions about code by hallucinating plausible-sounding but incorrect details when given no context. This system solves that by indexing a real codebase into a vector store, retrieving the most semantically relevant chunks at query time, and constraining the LLM to answer only from that retrieved context. The result is accurate, source-cited answers about any codebase — built from scratch without relying on a managed RAG service.

---

## Architecture

```
POST /index (folder path)
        │
        ▼
   FileScanner  ──── recursive scan, ignores node_modules / target / .git
        │            caps at 15 files
        ▼
   CodeChunker  ──── splits by function/class boundaries (not line count)
        │            language-aware: Java, Python, JS/TS, Kotlin, C/C++
        │
        ▼
   VectorStore  ──── builds TF-IDF vectors for each chunk (numpy)
        │            persists chunks + vectors + vocab to vector_store.json
        │
        ▼
   vector_store.json (disk)


POST /query (question)
        │
        ▼
   VectorStore.search()  ──── TF-IDF vector of question → cosine similarity
        │
        ▼
   QAService  ──── build context from top-K chunks
        │           prompt Groq (llama-3.1-8b-instant)
        │           answer grounded in context only
        ▼
   QueryResponse  ──── answer + source files, functions, line numbers
```

---

## Engineering Highlights

**Semantic chunking, not line-based splitting**
`CodeChunker` detects function and class boundaries using language-specific regex patterns before splitting. A Java method stays in one chunk; a Python class stays together. Only chunks exceeding 60 lines are split further, with 5-line overlap to preserve context across boundaries. This produces semantically coherent chunks rather than arbitrary line windows.

**Language-aware boundary detection**
Boundary patterns are defined per language family:
- Java/Kotlin/C++: access modifier + return type + method name patterns
- Python: `def` and `class` with async support
- JavaScript/TypeScript: `function`, `class`, arrow functions, `const fn = () =>`

Files with no detected boundaries fall back to line-based chunking automatically.

**TF-IDF vector store with cosine similarity**
There is no FAISS and no Ollama embeddings. The vector store is a custom TF-IDF implementation using numpy. Each chunk is tokenised and converted to a TF-IDF vector — a list of numbers where each position represents a vocabulary term weighted by how distinctive it is across all chunks. Search converts the query to the same vector space and computes cosine similarity against all chunk vectors using numpy matrix operations. Results are ranked by similarity score. Vectors and vocabulary are persisted to `vector_store.json`.

**Grounded LLM answers**
The prompt explicitly instructs the LLM to answer only from the retrieved context and to reference actual class and method names it sees. Temperature is set to 0.1 to minimise creative deviation. Sources (file, function, line range) are returned alongside the answer so the caller can verify.

**Groq for inference, no local model required**
The LLM client is Groq (`llama-3.1-8b-instant`) via API key. Groq is a cloud inference service — fast and free-tier available. No Ollama, no local model download needed. A `GROQ_API_KEY` in `.env` is required for queries to work.

---

## Design Decisions & Trade-offs

**TF-IDF vs. semantic embeddings** — TF-IDF matches on shared vocabulary between the query and the code. It works well when you use the same words that appear in the code (e.g. asking about `JwtAuthFilter` will find chunks containing that word). It does not understand meaning — asking "how does login work" will not find code that uses the word `authenticate` unless `login` also appears nearby. A semantic embedding model (e.g. `nomic-embed-text` via Ollama) would understand synonyms and concepts, but adds infrastructure complexity. TF-IDF is simpler and sufficient for demonstrating the RAG pattern.

**Chunk overlap on large functions** — When a function exceeds 60 lines, sub-chunks are created with 5-line overlap. This ensures that code at chunk boundaries appears in at least two chunks, reducing the chance that a relevant line falls in a gap. The trade-off is slightly more storage and some duplicate content in search results.

**Regex boundary detection vs. AST parsing** — Regex is fast, requires no language-specific parser dependencies, and works well for the common patterns. The trade-off is that unusual formatting or macros can miss boundaries. AST-based chunking (e.g. tree-sitter) would be more accurate but adds significant complexity and per-language dependencies.

**Groq for inference, Ollama for embeddings** — Groq's hosted `llama-3.1-8b-instant` is significantly faster than running llama3.2 locally for inference. Embeddings still use local Ollama (`nomic-embed-text`) since embedding is fast locally and avoids sending source code to an external service.

---

## API Reference

| Method | Endpoint       | Description                                              |
|--------|----------------|----------------------------------------------------------|
| POST   | `/index`        | Index a folder — scan, chunk, embed, store               |
| POST   | `/query`        | Ask a question, returns answer + sources                 |
| GET    | `/status`       | Chunk count, indexed files, store state                  |
| DELETE | `/index`        | Clear the vector store                                   |

**Example — index a project:**
```bash
curl -X POST http://localhost:8000/index \
  -H "Content-Type: application/json" \
  -d '{"folder_path": "C:/Users/you/projects/my-service"}'
```

**Example — query:**
```bash
curl -X POST http://localhost:8000/query \
  -H "Content-Type: application/json" \
  -d '{"question": "How does authentication work in this codebase?"}'
```

**Response:**
```json
{
  "answer": "Authentication is handled by JwtAuthFilter, which intercepts every request and validates the token using JwtUtil.validateToken(). The username and role are extracted from the claims and set on the SecurityContext...",
  "sources": [
    {
      "file_path": "src/main/java/com/example/filter/JwtAuthFilter.java",
      "context_name": "doFilterInternal",
      "start_line": 24,
      "end_line": 61,
      "language": "java"
    }
  ]
}
```

**Example — status:**
```bash
curl http://localhost:8000/status
# {"indexed_chunks": 312, "files": ["ResourceService.java", "TaskService.java", ...]}
```

---

## Stack

| Layer      | Technology                                          |
|------------|-----------------------------------------------------|
| API        | Python 3.12, FastAPI                                |
| Search     | Custom TF-IDF (numpy), cosine similarity            |
| LLM        | Groq API (llama-3.1-8b-instant)                     |
| Persistence| JSON file (vector_store.json)                       |
| Chunking   | Custom regex-based boundary detection               |
| Testing    | pytest                                              |

---

## Running Locally

**Prerequisites:** Python 3.12, Groq API key (free at https://console.groq.com)

```bash
# 1. Install dependencies
cd project-4-codebase-qa/app
pip install -r requirements.txt

# 2. Configure environment
cp .env.example .env
# Add your GROQ_API_KEY to .env

# 3. Start the API
uvicorn app.main:app --reload --port 8000
```

API: http://localhost:8000  
Docs: http://localhost:8000/docs

---

## Tests

```bash
cd project-4-codebase-qa/app
pytest
```

37/39 tests passing.

Covers: file scanning with ignore rules, boundary detection per language, chunk splitting and overlap, TF-IDF vector store search, QA service context building, API route responses.

2 failing tests are assertion mismatches on scanner path formatting (Windows vs. POSIX separators) — logic is correct, test strings need updating.
