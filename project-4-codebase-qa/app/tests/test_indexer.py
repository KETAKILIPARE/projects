import pytest
from unittest.mock import MagicMock, patch
from app.models.schemas import CodeChunk
from app.services.indexer import Indexer, MAX_FILES
from app.services.scanner import ScannedFile

FOLDER_PATH      = "/some/project"
FILE_PATH_1      = "/some/project/UserService.java"
FILE_PATH_2      = "/some/project/AuthService.java"
LANGUAGE_JAVA    = "java"
CONTENT_1        = "public class UserService {}"
CONTENT_2        = "public class AuthService {}"
CHUNK_COUNT_2    = 2
CHUNK_COUNT_0    = 0


def make_scanned_file(file_path, content):
    return ScannedFile(file_path=file_path, language=LANGUAGE_JAVA, content=content)


def make_chunk(chunk_id, file_path):
    return CodeChunk(
        chunk_id=chunk_id,
        file_path=file_path,
        language=LANGUAGE_JAVA,
        start_line=1,
        end_line=5,
        content="public class Foo {}",
        context_name=None,
    )


@pytest.fixture
def mock_store():
    store = MagicMock()
    store.count.return_value = CHUNK_COUNT_2
    return store


class TestIndexer:

    def test_index_folder_returns_indexed_file_count(self, mock_store):
        with patch("app.services.indexer.FileScanner") as mock_scanner_cls, \
             patch("app.services.indexer.CodeChunker") as mock_chunker_cls:

            mock_scanner_cls.return_value.scan.return_value = [
                make_scanned_file(FILE_PATH_1, CONTENT_1),
                make_scanned_file(FILE_PATH_2, CONTENT_2),
            ]
            mock_chunker_cls.return_value.chunk.return_value = [make_chunk("c1", FILE_PATH_1)]

            indexer = Indexer(vector_store=mock_store)
            response = indexer.index_folder(FOLDER_PATH)

            assert response.indexed_files == 2, \
                "should report the number of files that were indexed"

    def test_index_folder_returns_total_chunk_count_from_store(self, mock_store):
        with patch("app.services.indexer.FileScanner") as mock_scanner_cls, \
             patch("app.services.indexer.CodeChunker") as mock_chunker_cls:

            mock_scanner_cls.return_value.scan.return_value = [
                make_scanned_file(FILE_PATH_1, CONTENT_1),
            ]
            mock_chunker_cls.return_value.chunk.return_value = [make_chunk("c1", FILE_PATH_1)]

            indexer = Indexer(vector_store=mock_store)
            response = indexer.index_folder(FOLDER_PATH)

            assert response.total_chunks == CHUNK_COUNT_2, \
                "total_chunks should reflect the store count after indexing"

    def test_index_folder_calls_store_add_chunks(self, mock_store):
        with patch("app.services.indexer.FileScanner") as mock_scanner_cls, \
             patch("app.services.indexer.CodeChunker") as mock_chunker_cls:

            chunk = make_chunk("c1", FILE_PATH_1)
            mock_scanner_cls.return_value.scan.return_value = [
                make_scanned_file(FILE_PATH_1, CONTENT_1),
            ]
            mock_chunker_cls.return_value.chunk.return_value = [chunk]

            indexer = Indexer(vector_store=mock_store)
            indexer.index_folder(FOLDER_PATH)

            mock_store.add_chunks.assert_called_once(), \
                "should call add_chunks on the vector store"

    def test_index_folder_caps_files_at_max_files(self, mock_store):
        with patch("app.services.indexer.FileScanner") as mock_scanner_cls, \
             patch("app.services.indexer.CodeChunker") as mock_chunker_cls:

            many_files = [
                make_scanned_file(f"/project/File{i}.java", f"class File{i} {{}}")
                for i in range(MAX_FILES + 5)
            ]
            mock_scanner_cls.return_value.scan.return_value = many_files
            mock_chunker_cls.return_value.chunk.return_value = []

            indexer = Indexer(vector_store=mock_store)
            response = indexer.index_folder(FOLDER_PATH)

            assert response.indexed_files == MAX_FILES, \
                f"indexer should cap files at MAX_FILES ({MAX_FILES})"

    def test_index_folder_returns_zero_files_for_empty_folder(self, mock_store):
        mock_store.count.return_value = CHUNK_COUNT_0
        with patch("app.services.indexer.FileScanner") as mock_scanner_cls, \
             patch("app.services.indexer.CodeChunker"):

            mock_scanner_cls.return_value.scan.return_value = []

            indexer = Indexer(vector_store=mock_store)
            response = indexer.index_folder(FOLDER_PATH)

            assert response.indexed_files == 0, \
                "should report 0 indexed files for an empty folder"
            assert response.total_chunks == CHUNK_COUNT_0, \
                "should report 0 total chunks for an empty folder"
