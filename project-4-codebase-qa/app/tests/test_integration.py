import pytest
from app.services.scanner import FileScanner
from app.services.chunker import CodeChunker
from app.services.vector_store import VectorStore
from app.services.indexer import Indexer

JAVA_CONTENT = """\
public class UserService {

    private final UserRepository repository;

    public UserService(UserRepository repository) {
        this.repository = repository;
    }

    public User findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    public User save(User user) {
        return repository.save(user);
    }
}
"""

PYTHON_CONTENT = """\
def calculate_hash(data: str) -> str:
    import hashlib
    return hashlib.sha256(data.encode()).hexdigest()


def validate_email(email: str) -> bool:
    return "@" in email and "." in email
"""

QUERY_FIND_USER  = "find user by id"
QUERY_HASH       = "calculate hash sha256"
LANGUAGE_JAVA    = "java"
LANGUAGE_PYTHON  = "python"
FILE_USER        = "UserService.java"
FILE_UTILS       = "utils.py"


@pytest.fixture
def temp_project(tmp_path):
    (tmp_path / FILE_USER).write_text(JAVA_CONTENT)
    (tmp_path / FILE_UTILS).write_text(PYTHON_CONTENT)
    ignored = tmp_path / "node_modules" / "lib"
    ignored.mkdir(parents=True)
    (ignored / "index.js").write_text("module.exports = {};")
    return tmp_path


class TestFullPipeline:

    def test_scanner_finds_both_source_files(self, temp_project):
        scanner = FileScanner()
        files = scanner.scan(str(temp_project))
        paths = [f.file_path for f in files]
        assert any(FILE_USER in p for p in paths), \
            "scanner should find the Java source file"
        assert any(FILE_UTILS in p for p in paths), \
            "scanner should find the Python source file"

    def test_scanner_ignores_node_modules(self, temp_project):
        scanner = FileScanner()
        files = scanner.scan(str(temp_project))
        paths = [f.file_path.replace("\\", "/") for f in files]
        assert not any("node_modules" in p for p in paths), \
            "scanner should not include files from node_modules"

    def test_chunker_produces_chunks_from_scanned_files(self, temp_project):
        scanner = FileScanner()
        chunker = CodeChunker()
        files = scanner.scan(str(temp_project))
        all_chunks = []
        for f in files:
            all_chunks.extend(chunker.chunk(f.content, f.file_path, f.language))
        assert len(all_chunks) > 0, \
            "chunker should produce at least one chunk from the scanned files"

    def test_vector_store_indexes_all_chunks(self, temp_project):
        scanner = FileScanner()
        chunker = CodeChunker()
        store = VectorStore(persist_path=":memory:")
        files = scanner.scan(str(temp_project))
        all_chunks = []
        for f in files:
            all_chunks.extend(chunker.chunk(f.content, f.file_path, f.language))
        store.add_chunks(all_chunks)
        assert store.count() == len(all_chunks), \
            "vector store should contain exactly the number of chunks produced"

    def test_vector_store_search_returns_relevant_result(self, temp_project):
        scanner = FileScanner()
        chunker = CodeChunker()
        store = VectorStore(persist_path=":memory:")
        files = scanner.scan(str(temp_project))
        for f in files:
            store.add_chunks(chunker.chunk(f.content, f.file_path, f.language))
        results = store.search(QUERY_HASH, top_k=3)
        assert len(results) > 0, \
            "search should return results after indexing"
        assert any(FILE_UTILS in r.file_path for r in results), \
            "search for hash should return a result from the Python utils file"

    def test_indexer_wires_full_pipeline(self, tmp_path):
        (tmp_path / FILE_USER).write_text(JAVA_CONTENT)
        (tmp_path / FILE_UTILS).write_text(PYTHON_CONTENT)
        store = VectorStore(persist_path=":memory:")
        indexer = Indexer(vector_store=store)
        response = indexer.index_folder(str(tmp_path))
        assert response.indexed_files == 2, \
            "indexer should report 2 indexed files"
        assert response.total_chunks > 0, \
            "indexer should produce at least one chunk"
        assert store.count() > 0, \
            "vector store should be populated after indexing"

    def test_indexer_then_search_finds_relevant_chunk(self, tmp_path):
        (tmp_path / FILE_USER).write_text(JAVA_CONTENT)
        store = VectorStore(persist_path=":memory:")
        indexer = Indexer(vector_store=store)
        indexer.index_folder(str(tmp_path))
        results = store.search(QUERY_FIND_USER, top_k=3)
        assert len(results) > 0, \
            "should find results after indexing"
        assert any(FILE_USER in r.file_path for r in results), \
            "search for 'find user' should return a chunk from UserService.java"
