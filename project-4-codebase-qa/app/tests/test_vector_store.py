import json
import pytest
from app.models.schemas import CodeChunk
from app.services.vector_store import VectorStore

CHUNK_ID_1       = "chunk_1"
CHUNK_ID_2       = "chunk_2"
CHUNK_ID_3       = "chunk_3"
FILE_USER        = "UserService.java"
FILE_AUTH        = "AuthService.java"
FILE_UTILS       = "utils.py"
LANGUAGE_JAVA    = "java"
LANGUAGE_PYTHON  = "python"
CONTENT_FIND_USER   = "public User findById(Long id) { return repository.findById(id); }"
CONTENT_GEN_TOKEN   = "public String generateToken(String username) { return jwtUtil.generate(username); }"
CONTENT_HASH        = "def calculate_hash(data): return hashlib.sha256(data.encode()).hexdigest()"
CONTEXT_FIND_BY_ID  = "findById"
CONTEXT_GEN_TOKEN   = "generateToken"
CONTEXT_HASH        = "calculate_hash"
QUERY_FIND_USER     = "find user by id repository"
QUERY_AUTH_TOKEN    = "authentication token jwt generate"
TOP_K_2          = 2
TOP_K_3          = 3
TOP_K_100        = 100


def make_chunk(chunk_id, file_path, language, content, context_name):
    return CodeChunk(
        chunk_id=chunk_id,
        file_path=file_path,
        language=language,
        start_line=1,
        end_line=10,
        content=content,
        context_name=context_name,
    )


@pytest.fixture
def sample_chunks():
    return [
        make_chunk(CHUNK_ID_1, FILE_USER, LANGUAGE_JAVA, CONTENT_FIND_USER, CONTEXT_FIND_BY_ID),
        make_chunk(CHUNK_ID_2, FILE_AUTH, LANGUAGE_JAVA, CONTENT_GEN_TOKEN, CONTEXT_GEN_TOKEN),
        make_chunk(CHUNK_ID_3, FILE_UTILS, LANGUAGE_PYTHON, CONTENT_HASH, CONTEXT_HASH),
    ]


class TestVectorStore:

    def test_add_chunks_stores_all_chunks(self, sample_chunks):
        store = VectorStore(persist_path=":memory:")
        store.add_chunks(sample_chunks)
        assert store.count() == 3, \
            "store should contain all 3 added chunks"

    def test_add_chunks_is_idempotent(self, sample_chunks):
        store = VectorStore(persist_path=":memory:")
        store.add_chunks(sample_chunks)
        store.add_chunks(sample_chunks)
        assert store.count() == 3, \
            "adding the same chunks twice should not increase the count"

    def test_search_returns_at_most_top_k_results(self, sample_chunks):
        store = VectorStore(persist_path=":memory:")
        store.add_chunks(sample_chunks)
        results = store.search(QUERY_FIND_USER, top_k=TOP_K_2)
        assert len(results) <= TOP_K_2, \
            f"search should return at most {TOP_K_2} results"

    def test_search_returns_code_chunks(self, sample_chunks):
        store = VectorStore(persist_path=":memory:")
        store.add_chunks(sample_chunks)
        results = store.search(QUERY_AUTH_TOKEN, top_k=TOP_K_3)
        assert all(isinstance(r, CodeChunk) for r in results), \
            "all search results should be CodeChunk instances"

    def test_search_ranks_relevant_chunk_first(self, sample_chunks):
        store = VectorStore(persist_path=":memory:")
        store.add_chunks(sample_chunks)
        results = store.search(QUERY_AUTH_TOKEN, top_k=TOP_K_3)
        assert results[0].chunk_id == CHUNK_ID_2, \
            "most relevant chunk for auth token query should be ranked first"

    def test_search_returns_empty_when_store_is_empty(self):
        store = VectorStore(persist_path=":memory:")
        results = store.search(QUERY_FIND_USER, top_k=TOP_K_3)
        assert results == [], \
            "search on empty store should return empty list"

    def test_search_top_k_larger_than_chunks_returns_all(self, sample_chunks):
        store = VectorStore(persist_path=":memory:")
        store.add_chunks(sample_chunks)
        results = store.search(QUERY_FIND_USER, top_k=TOP_K_100)
        assert len(results) == 3, \
            "when top_k exceeds chunk count, all chunks should be returned"

    def test_clear_removes_all_chunks(self, sample_chunks):
        store = VectorStore(persist_path=":memory:")
        store.add_chunks(sample_chunks)
        store.clear()
        assert store.count() == 0, \
            "clear should remove all chunks from the store"

    def test_clear_makes_search_return_empty(self, sample_chunks):
        store = VectorStore(persist_path=":memory:")
        store.add_chunks(sample_chunks)
        store.clear()
        assert store.search(QUERY_FIND_USER, top_k=TOP_K_3) == [], \
            "search after clear should return empty list"

    def test_get_indexed_files_returns_unique_paths(self, sample_chunks):
        store = VectorStore(persist_path=":memory:")
        store.add_chunks(sample_chunks)
        files = store.get_indexed_files()
        assert set(files) == {FILE_USER, FILE_AUTH, FILE_UTILS}, \
            "get_indexed_files should return all unique file paths"

    def test_persist_saves_and_reloads_chunks(self, tmp_path, sample_chunks):
        persist_file = str(tmp_path / "store.json")
        store = VectorStore(persist_path=persist_file)
        store.add_chunks(sample_chunks)

        reloaded = VectorStore(persist_path=persist_file)
        assert reloaded.count() == 3, \
            "reloaded store should contain all chunks that were persisted"

    def test_clear_deletes_persist_file(self, tmp_path, sample_chunks):
        import os
        persist_file = str(tmp_path / "store.json")
        store = VectorStore(persist_path=persist_file)
        store.add_chunks(sample_chunks)
        store.clear()
        assert not os.path.exists(persist_file), \
            "clear should delete the persistence file from disk"
