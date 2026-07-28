# Project 4 — Codebase Q&A Assistant: In-Depth Guide

---

## What Is This Project?

This is a Python service that lets you point it at any folder of source code and then ask questions about that code in plain English. It scans the files, splits them into chunks, builds a searchable index, and uses an LLM to answer your questions based on what it finds.

It is a portfolio project built to practise the concepts behind Retrieval-Augmented Generation (RAG): scanning, chunking, vector search, and grounded LLM inference.

The backend is a FastAPI REST API. There is no frontend (a simple HTML page is planned). The index is persisted to a JSON file on disk.

---

## What Does It Actually Do?

1. You call `POST /index` with a folder path.
2. The service scans every supported source file in that folder (recursively), skipping build directories and dependencies.
3. Each file is split into chunks — ideally one chunk per function or class.
4. Each chunk is converted into a vector (a list of numbers representing its meaning) using TF-IDF.
5. All vectors are stored in memory (and persisted to `vector_store.json`).
6. You call `POST /query` with a question.
7. The question is also converted to a TF-IDF vector.
8. The system finds the top-K most similar chunks using cosine similarity.
9. Those chunks are sent to an LLM (Groq) as context, along with your question.
10. The LLM answers based only on that context. The answer is returned along with the source files and line numbers.

---

## The Pipeline in Detail

### Step 1: File Scanning (`FileScanner`)

`FileScanner.scan(folder_path)` walks the folder recursively using `os.walk`. It:
- Skips any directory whose name is in the `IGNORED_DIRS` set: `node_modules`, `target`, `.git`, `__pycache__`, `dist`, `build`, `out`, `.gradle`, `vendor`, `venv`, `.env`, `coverage`, `.idea`, `.vscode`.
- Only reads files with supported extensions: `.java`, `.py`, `.js`, `.ts`, `.jsx`, `.tsx`, `.go`, `.rs`, `.cs`, `.cpp`, `.c`, `.rb`, `.kt`, `.scala`, `.php`, `.swift`.
- Reads each file as UTF-8 text (with `errors="ignore"` so binary content doesn't crash it).
- Returns a list of `ScannedFile` objects, each with `file_path`, `language`, and `content`.

**Important:** The `Indexer` caps the number of files at 15 (`MAX_FILES = 15`). Only the first 15 files returned by the scanner are indexed. This is a deliberate limit to keep indexing fast during development.

### Step 2: Chunking (`CodeChunker`)

`CodeChunker.chunk(content, file_path, language)` splits a file's content into smaller pieces.

**Boundary detection:**
The chunker first tries to find function and class boundaries using regex patterns specific to the language:
- Python: lines matching `def <name>(` or `class <name>`
- Java/Kotlin/C++/C#: lines matching access modifier + return type + method name, or `class`/`interface`/`enum` declarations
- JavaScript/TypeScript: `function`, `class`, arrow functions, `const fn = (`

It returns a list of `(line_index, name)` tuples — the line where each function/class starts and its name.

**Chunking by boundaries:**
If boundaries are found, each chunk runs from one boundary to the line before the next boundary. If a chunk would be longer than 60 lines, it is split into sub-chunks with 5-line overlap between them (so code at the boundary of two sub-chunks appears in both, reducing the chance of losing context).

**Fallback:**
If no boundaries are detected (e.g. a config file or a language not in the pattern list), the file is split into 60-line windows with 5-line overlap.

**Each chunk** is a `CodeChunk` with:
- `chunk_id` — MD5 hash of `file_path:start_line:end_line:first50chars`
- `file_path`, `language`, `start_line`, `end_line`
- `content` — the actual code text
- `context_name` — the function or class name if detected, otherwise `None`

### Step 3: Vectorisation and Storage (`VectorStore`)

This is the most important thing to understand correctly: **this project does NOT use FAISS or Ollama embeddings**. It uses a custom TF-IDF implementation built with numpy.

**What is TF-IDF?**
TF-IDF (Term Frequency–Inverse Document Frequency) is a way to represent text as a vector of numbers. Each position in the vector corresponds to a word in the vocabulary. The value at each position reflects how important that word is in this particular document relative to all documents.

- **TF (Term Frequency):** how often a word appears in this chunk, divided by total words in the chunk.
- **IDF (Inverse Document Frequency):** `log((total_chunks + 1) / (chunks_containing_word + 1)) + 1`. Words that appear in many chunks get a lower IDF (they are less distinctive). Words that appear in few chunks get a higher IDF (they are more distinctive).
- **TF-IDF score** = TF × IDF.

**How the VectorStore works:**

When `add_chunks(chunks)` is called:
1. New chunks are added to `self._chunks` (a dict keyed by `chunk_id`).
2. `_rebuild_vectors()` is called — this recomputes TF-IDF vectors for ALL chunks from scratch every time new chunks are added. This is correct but slow for large codebases.
3. The vocabulary is rebuilt from all tokens across all chunks.
4. IDF is computed for every term in the vocabulary.
5. Each chunk gets a TF-IDF vector of length `len(vocabulary)`.
6. Everything is saved to `vector_store.json`.

**Tokenisation:**
Text is lowercased, then split on non-alphanumeric characters. Only tokens of length > 1 are kept. So `getUserById` becomes `getuserbyid` as one token (camelCase is not split). This is a limitation — `getUserById` and `getUser` would not share any tokens.

**Search:**
When `search(query, top_k)` is called:
1. The query is tokenised and converted to a TF-IDF vector using the stored vocabulary and IDF values.
2. A matrix of all chunk vectors is built with numpy.
3. Cosine similarity is computed: `dot(matrix, query) / (norm(matrix) * norm(query))`.
4. The top-K chunks by similarity score are returned.

**Persistence:**
The chunks, vectors, and vocabulary are saved to `vector_store.json` as JSON. On startup, if the file exists, it is loaded automatically. This means you do not need to re-index every time you restart the server.

### Step 4: LLM Answering (`QAService`)

`QAService` uses the Groq API to answer questions. Groq is a cloud service that runs LLMs very fast. The model used is `llama-3.1-8b-instant`.

When `query(question, top_k)` is called:
1. If the store is empty, it returns a message saying nothing has been indexed.
2. `vector_store.search(question, top_k=5)` retrieves the most relevant chunks.
3. `_build_context(chunks)` formats each chunk as:
   ```
   File: ResourceService.java | Function/Class: create | Lines 34-67
   ```java
   ... code content ...
   ```
   ```
4. The context and question are inserted into a prompt template that instructs the LLM to answer only from the provided context and to reference actual class and method names.
5. The Groq client sends the prompt with `temperature=0.1` (low temperature = less creative, more factual).
6. The answer and the source chunks are returned.

**Groq requires an API key.** Set `GROQ_API_KEY` in the `.env` file. Without it, queries will fail.

---

## What the API Looks Like

### `POST /index`
```json
{ "folder_path": "C:/Users/you/projects/my-service" }
```
Response:
```json
{ "indexed_files": 12, "total_chunks": 87, "folder_path": "C:/..." }
```

### `POST /query`
```json
{ "question": "How does authentication work?", "top_k": 5 }
```
Response:
```json
{
  "answer": "Authentication is handled by JwtAuthFilter...",
  "sources": [
    {
      "chunk_id": "abc123",
      "file_path": "...",
      "language": "java",
      "start_line": 24,
      "end_line": 61,
      "content": "...",
      "context_name": "doFilterInternal"
    }
  ]
}
```

### `GET /status`
```json
{ "total_chunks": 87, "indexed_files": ["ResourceService.java", ...], "is_ready": true }
```

### `DELETE /index`
Clears the vector store and deletes `vector_store.json`. Returns 204.

---

## How the Code Is Organised

```
app/
├── app/
│   ├── api/
│   │   └── routes.py       — FastAPI router: /index, /query, /status, DELETE /index
│   ├── models/
│   │   └── schemas.py      — Pydantic models: CodeChunk, IndexRequest/Response, QueryRequest/Response, StatusResponse
│   ├── services/
│   │   ├── scanner.py      — FileScanner: recursive file scan with ignore rules
│   │   ├── chunker.py      — CodeChunker: boundary detection + chunk splitting
│   │   ├── vector_store.py — VectorStore: TF-IDF vectors, cosine similarity search, JSON persistence
│   │   ├── indexer.py      — Indexer: orchestrates scan → chunk → store (caps at 15 files)
│   │   └── qa_service.py   — QAService: retrieves chunks, builds prompt, calls Groq
│   └── main.py             — FastAPI app setup, includes router
├── tests/                  — pytest tests (37/39 passing)
├── .env                    — GROQ_API_KEY goes here
├── .env.example
├── requirements.txt
├── vector_store.json       — persisted index (created after first /index call)
└── pytest.ini
```

---

## The 2 Failing Tests

The 2 failing tests are in the scanner tests. They assert on file paths using forward slashes (POSIX style), but on Windows `os.path.join` returns backslashes. The scanner logic is correct — the test assertion strings just need to be updated to use `os.path.join` or `os.sep` instead of hardcoded `/`.

---

## How to Run It

### Prerequisites
- Python 3.12
- A Groq API key (free at https://console.groq.com)

### Step 1 — Install Dependencies

```bash
cd project-4-codebase-qa/app
pip install -r requirements.txt
```

### Step 2 — Configure Environment

```bash
cp .env.example .env
```

Open `.env` and add your Groq API key:
```
GROQ_API_KEY=gsk_your_key_here
```

### Step 3 — Start the API

```bash
uvicorn app.main:app --reload --port 8000
```

API starts on http://localhost:8000  
Interactive docs at http://localhost:8000/docs

### Step 4 — Index a Project

Point it at any folder of source code. For example, index Project 1:

```bash
curl -X POST http://localhost:8000/index \
  -H "Content-Type: application/json" \
  -d '{"folder_path": "C:/Users/yourname/Desktop/bubu/project-1-cloud-resource/backend/src"}'
```

You will see output in the terminal showing files being chunked and indexed.

### Step 5 — Ask a Question

```bash
curl -X POST http://localhost:8000/query \
  -H "Content-Type: application/json" \
  -d '{"question": "How does role-based access control work in this codebase?"}'
```

The response will include an answer and the exact source files and line numbers the answer was drawn from.

### Step 6 — Check Status

```bash
curl http://localhost:8000/status
```

### Running the Tests

```bash
cd project-4-codebase-qa/app
pytest
```

37/39 passing. The 2 failures are path separator assertions in the scanner tests (Windows vs POSIX).
