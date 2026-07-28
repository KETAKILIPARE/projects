import pytest
from unittest.mock import MagicMock, patch
from fastapi.testclient import TestClient
from app.models.schemas import CodeChunk, IndexResponse, QueryResponse

FOLDER_PATH         = "/some/project"
EMPTY_FOLDER_PATH   = ""
QUESTION_JWT        = "How is JWT generated?"
QUESTION_EMPTY      = ""
QUESTION_WHITESPACE = "   "
FILE_AUTH           = "AuthService.java"
FILE_USER           = "UserService.java"
LANGUAGE_JAVA       = "java"
CONTENT_GEN_TOKEN   = "public String generateToken(String username) {}"
CONTEXT_GEN_TOKEN   = "generateToken"
LLM_ANSWER          = "JWT is generated in AuthService using jwtUtil."
INDEXED_FILES_COUNT = 5
TOTAL_CHUNKS_COUNT  = 42
TOP_K_3             = 3


def make_source_chunk():
    return CodeChunk(
        chunk_id="chunk_1",
        file_path=FILE_AUTH,
        language=LANGUAGE_JAVA,
        start_line=5,
        end_line=15,
        content=CONTENT_GEN_TOKEN,
        context_name=CONTEXT_GEN_TOKEN,
    )


@pytest.fixture
def mock_indexer():
    with patch("app.api.routes.indexer") as mock:
        mock.index_folder.return_value = IndexResponse(
            indexed_files=INDEXED_FILES_COUNT,
            total_chunks=TOTAL_CHUNKS_COUNT,
            folder_path=FOLDER_PATH,
        )
        yield mock


@pytest.fixture
def mock_qa():
    with patch("app.api.routes.qa_service") as mock:
        mock.query.return_value = QueryResponse(
            answer=LLM_ANSWER,
            sources=[make_source_chunk()],
        )
        yield mock


@pytest.fixture
def mock_store():
    with patch("app.api.routes.vector_store") as mock:
        mock.count.return_value = TOTAL_CHUNKS_COUNT
        mock.get_indexed_files.return_value = [FILE_AUTH, FILE_USER]
        yield mock


@pytest.fixture
def client(mock_indexer, mock_qa, mock_store):
    from app.main import app
    return TestClient(app)


class TestAPIRoutes:

    def test_index_returns_201_with_stats(self, client):
        response = client.post("/index", json={"folder_path": FOLDER_PATH})
        assert response.status_code == 201, "POST /index should return 201 on success"
        data = response.json()
        assert data["indexed_files"] == INDEXED_FILES_COUNT, \
            "response should include the number of indexed files"
        assert data["total_chunks"] == TOTAL_CHUNKS_COUNT, \
            "response should include the total chunk count"

    def test_index_returns_400_for_empty_path(self, client):
        response = client.post("/index", json={"folder_path": EMPTY_FOLDER_PATH})
        assert response.status_code == 400, \
            "POST /index should return 400 when folder_path is empty"

    def test_index_returns_400_when_folder_does_not_exist(self, client, mock_indexer):
        mock_indexer.index_folder.side_effect = ValueError("Folder does not exist")
        response = client.post("/index", json={"folder_path": "/nonexistent/path"})
        assert response.status_code == 400, \
            "POST /index should return 400 when folder does not exist"

    def test_query_returns_200_with_answer_and_sources(self, client):
        response = client.post("/query", json={"question": QUESTION_JWT})
        assert response.status_code == 200, "POST /query should return 200 on success"
        data = response.json()
        assert "answer" in data, "response should contain an answer field"
        assert len(data["sources"]) > 0, "response should contain at least one source"

    def test_query_returns_400_for_empty_question(self, client):
        response = client.post("/query", json={"question": QUESTION_EMPTY})
        assert response.status_code == 400, \
            "POST /query should return 400 when question is empty"

    def test_query_returns_400_for_whitespace_question(self, client):
        response = client.post("/query", json={"question": QUESTION_WHITESPACE})
        assert response.status_code == 400, \
            "POST /query should return 400 when question is only whitespace"

    def test_query_respects_top_k(self, client, mock_qa):
        client.post("/query", json={"question": QUESTION_JWT, "top_k": TOP_K_3})
        mock_qa.query.assert_called_once_with(QUESTION_JWT, top_k=TOP_K_3), \
            "query should be called with the exact top_k value from the request"

    def test_status_returns_chunk_count_and_files(self, client):
        response = client.get("/status")
        assert response.status_code == 200, "GET /status should return 200"
        data = response.json()
        assert data["total_chunks"] == TOTAL_CHUNKS_COUNT, \
            "status should return the correct total chunk count"
        assert FILE_AUTH in data["indexed_files"], \
            "status should list indexed files"
        assert data["is_ready"] is True, \
            "is_ready should be True when chunks exist"

    def test_status_is_ready_false_when_empty(self, client, mock_store):
        mock_store.count.return_value = 0
        mock_store.get_indexed_files.return_value = []
        response = client.get("/status")
        assert response.json()["is_ready"] is False, \
            "is_ready should be False when no chunks are indexed"

    def test_delete_index_returns_204(self, client):
        response = client.delete("/index")
        assert response.status_code == 204, \
            "DELETE /index should return 204 No Content"

    def test_delete_index_calls_vector_store_clear(self, client, mock_store):
        client.delete("/index")
        mock_store.clear.assert_called_once(), \
            "DELETE /index should call vector_store.clear()"
