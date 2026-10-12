import logging
import os
import time
import traceback
from typing import Any, Dict, List, Optional
from concurrent.futures import ThreadPoolExecutor

from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException, APIRouter, BackgroundTasks
from fastapi.responses import JSONResponse, RedirectResponse
from pydantic import BaseModel, Field

from mem0 import Memory

# Load environment variables
load_dotenv()

# Configure logging level from environment variable (default: INFO, set DEBUG_MODE=true for DEBUG)
DEBUG_MODE = os.environ.get("DEBUG_MODE", "false").lower() == "true"
LOG_LEVEL = logging.DEBUG if DEBUG_MODE else logging.INFO

# Configure root logger
logging.basicConfig(
    level=LOG_LEVEL,
    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S"
)

# Set mem0 library loggers to DEBUG if in debug mode
if DEBUG_MODE:
    logging.getLogger("mem0").setLevel(logging.DEBUG)
    logging.getLogger("mem0.memory").setLevel(logging.DEBUG)
    logging.getLogger("mem0.vector_stores").setLevel(logging.DEBUG)
    logging.getLogger("mem0.llms").setLevel(logging.DEBUG)
    logging.getLogger("mem0.embeddings").setLevel(logging.DEBUG)
    logging.getLogger("mem0.graphs").setLevel(logging.DEBUG)
    logging.info("DEBUG MODE ENABLED - Detailed logging activated")

# Set other library loggers to INFO or higher (suppress DEBUG logs)
logging.getLogger("neo4j").setLevel(logging.INFO)
logging.getLogger("neo4j.io").setLevel(logging.INFO)
logging.getLogger("neo4j.pool").setLevel(logging.INFO)
logging.getLogger("httpcore").setLevel(logging.INFO)
logging.getLogger("httpcore.http11").setLevel(logging.INFO)
logging.getLogger("httpx").setLevel(logging.INFO)
logging.getLogger("openai").setLevel(logging.INFO)
logging.getLogger("openai._base_client").setLevel(logging.INFO)
logging.getLogger("urllib3").setLevel(logging.INFO)


POSTGRES_HOST = os.environ.get("POSTGRES_HOST", "localhost")
POSTGRES_PORT = os.environ.get("POSTGRES_PORT", "8432")
POSTGRES_DB = os.environ.get("POSTGRES_DB", "postgres")
POSTGRES_USER = os.environ.get("POSTGRES_USER", "postgres")
POSTGRES_PASSWORD = os.environ.get("POSTGRES_PASSWORD", "postgres")
POSTGRES_COLLECTION_NAME = os.environ.get("POSTGRES_COLLECTION_NAME", "memories")

NEO4J_URI = os.environ.get("NEO4J_URI", "bolt://localhost:8687")
NEO4J_USERNAME = os.environ.get("NEO4J_USERNAME", "neo4j")
NEO4J_PASSWORD = os.environ.get("NEO4J_PASSWORD", "mem0graph")

MEMGRAPH_URI = os.environ.get("MEMGRAPH_URI", "bolt://localhost:7687")
MEMGRAPH_USERNAME = os.environ.get("MEMGRAPH_USERNAME", "memgraph")
MEMGRAPH_PASSWORD = os.environ.get("MEMGRAPH_PASSWORD", "mem0graph")

OPENAI_API_KEY = os.environ.get("OPENAI_API_KEY")
HISTORY_DB_PATH = os.environ.get("HISTORY_DB_PATH", os.path.join(os.path.dirname(__file__), "history", "history.db"))

DEFAULT_CONFIG = {
    "version": "v1.1",
    "vector_store": {
        "provider": "pgvector",
        "config": {
            "host": POSTGRES_HOST,
            "port": int(POSTGRES_PORT),
            "dbname": POSTGRES_DB,
            "user": POSTGRES_USER,
            "password": POSTGRES_PASSWORD,
            "collection_name": POSTGRES_COLLECTION_NAME,
        },
    },
    "graph_store": {
        "provider": "neo4j",
        "config": {"url": NEO4J_URI, "username": NEO4J_USERNAME, "password": NEO4J_PASSWORD},
    },
    "llm": {"provider": "openai", "config": {"api_key": OPENAI_API_KEY, "temperature": 0.2, "model": "gpt-4.1-nano-2025-04-14"}},
    "embedder": {"provider": "openai", "config": {"api_key": OPENAI_API_KEY, "model": "text-embedding-3-small"}},
    "history_db_path": HISTORY_DB_PATH,
}


MEMORY_INSTANCE = Memory.from_config(DEFAULT_CONFIG)

app = FastAPI(
    title="Mem0 REST APIs",
    description="A REST API for managing and searching memories for your AI Agents and Apps.",
    version="1.0.0",
)

# Create API router with v1 prefix
api_router = APIRouter(prefix="/v1")

# Create API router with v2 prefix for MemoryClient compatibility
api_router_v2 = APIRouter(prefix="/v2")


class Message(BaseModel):
    role: str = Field(..., description="Role of the message (user or assistant).")
    content: str = Field(..., description="Message content.")


class MemoryCreate(BaseModel):
    messages: List[Message] = Field(..., description="List of messages to store.")
    user_id: Optional[str] = None
    agent_id: Optional[str] = None
    run_id: Optional[str] = None
    metadata: Optional[Dict[str, Any]] = None
    async_mode: Optional[bool] = False  # Support async_mode parameter from MemoryClient


class SearchRequest(BaseModel):
    query: str = Field(..., description="Search query.")
    user_id: Optional[str] = None
    run_id: Optional[str] = None
    agent_id: Optional[str] = None
    filters: Optional[Dict[str, Any]] = None


class GetAllMemoriesRequest(BaseModel):
    """Request model for POST /v2/memories/ endpoint."""
    user_id: Optional[str] = None
    run_id: Optional[str] = None
    agent_id: Optional[str] = None


@api_router.post("/configure", summary="Configure Mem0")
def set_config(config: Dict[str, Any]):
    """Set memory configuration."""
    global MEMORY_INSTANCE
    MEMORY_INSTANCE = Memory.from_config(config)
    return {"message": "Configuration set successfully"}


def _add_memory_sync(messages: List[Dict], params: Dict[str, Any]) -> Dict[str, Any]:
    """Synchronous memory addition helper."""
    return MEMORY_INSTANCE.add(messages=messages, **params)


def _add_memory_background(messages: List[Dict], params: Dict[str, Any]):
    """Background task for async memory addition with error handling."""
    bg_start_time = time.time()
    try:
        logging.info(f"Background task started: processing {len(messages)} messages for user_id={params.get('user_id')}, agent_id={params.get('agent_id')}")
        logging.debug(f"Background task params: {params}")
        logging.debug(f"Background task messages: {messages}")
        
        # Call MEMORY_INSTANCE.add() directly with detailed logging
        logging.info(f"Calling MEMORY_INSTANCE.add() with {len(messages)} messages")
        
        # Verify MEMORY_INSTANCE is available
        if MEMORY_INSTANCE is None:
            raise RuntimeError("MEMORY_INSTANCE is None - cannot add memory")
        logging.debug(f"MEMORY_INSTANCE available: {type(MEMORY_INSTANCE)}")
        
        # Verify vector store connection
        if hasattr(MEMORY_INSTANCE, 'vector_store'):
            vs_type = type(MEMORY_INSTANCE.vector_store).__name__
            logging.debug(f"Vector store type: {vs_type}")
            if hasattr(MEMORY_INSTANCE.vector_store, 'connection_pool'):
                pool = MEMORY_INSTANCE.vector_store.connection_pool
                if pool is None:
                    logging.error("Vector store connection pool is None!")
                else:
                    logging.debug(f"Connection pool available: {type(pool)}")
        
        response = _add_memory_sync(messages, params)
        bg_processing_time = time.time() - bg_start_time
        
        # Log response structure in detail
        logging.info(f"MEMORY_INSTANCE.add() returned: type={type(response)}, response={response}")
        
        # Log detailed information about why results might be empty
        if isinstance(response, dict):
            results = response.get('results', [])
            if len(results) == 0:
                logging.warning("⚠️ MEMORY_INSTANCE.add() returned empty results array!")
                logging.warning(f"   - Input messages: {messages}")
                logging.warning(f"   - Params: {params}")
                logging.warning(f"   - This might mean:")
                logging.warning(f"     1. LLM did not extract any facts from the messages")
                logging.warning(f"     2. LLM determined no memory actions are needed (all NONE)")
                logging.warning(f"     3. LLM response parsing failed")
                logging.warning(f"   - Try setting infer=False to force direct memory storage")
        
        # Check response structure - Memory.add() returns {"results": [...]}
        if response:
            if isinstance(response, dict):
                results = response.get('results', [])
                memory_count = len(results) if isinstance(results, list) else 0
                logging.info(f"Background task completed successfully in {bg_processing_time:.3f}s - created {memory_count} memory(ies)")
                
                if isinstance(results, list) and len(results) > 0:
                    memory_ids = [m.get('id', 'unknown') for m in results if isinstance(m, dict)]
                    memory_events = [m.get('event', 'unknown') for m in results if isinstance(m, dict)]
                    logging.info(f"Created memory IDs: {memory_ids}")
                    logging.info(f"Memory events: {memory_events}")
                    # Log first memory detail
                    if len(results) > 0:
                        logging.debug(f"First memory detail: {results[0]}")
                
                if response.get('relations'):
                    logging.debug(f"Graph relations created: {response['relations']}")
            elif isinstance(response, list):
                # Handle list response (shouldn't happen with current Memory.add() but just in case)
                memory_count = len(response)
                logging.info(f"Background task completed successfully in {bg_processing_time:.3f}s - created {memory_count} memory(ies) (list response)")
                memory_ids = [m.get('id', 'unknown') for m in response if isinstance(m, dict)]
                logging.info(f"Created memory IDs: {memory_ids}")
            else:
                logging.warning(f"Background task returned unexpected response type: {type(response)}, value: {response}")
        else:
            logging.warning(f"Background task completed but response is empty or None")
        
        return response
    except Exception as e:
        bg_processing_time = time.time() - bg_start_time
        logging.exception(f"Background task failed after {bg_processing_time:.3f}s: {e}")
        logging.error(f"Error type: {type(e).__name__}, Error details: {str(e)}")
        logging.error(f"Traceback: {traceback.format_exc()}")


@api_router.post("/memories", summary="Create memories")
def add_memory(memory_create: MemoryCreate, background_tasks: BackgroundTasks):
    """Store new memories with optional async_mode support."""
    start_time = time.time()
    
    if not any([memory_create.user_id, memory_create.agent_id, memory_create.run_id]):
        raise HTTPException(status_code=400, detail="At least one identifier (user_id, agent_id, run_id) is required.")

    params = {k: v for k, v in memory_create.model_dump().items() if v is not None and k not in ["messages", "async_mode"]}
    messages = [m.model_dump() for m in memory_create.messages]
    
    # Log request details
    logging.info(f"add_memory request: user_id={memory_create.user_id}, agent_id={memory_create.agent_id}, "
                 f"run_id={memory_create.run_id}, async_mode={memory_create.async_mode}, "
                 f"messages_count={len(messages)}")
    
    try:
        if memory_create.async_mode:
            # Async mode: return immediately and process in background
            logging.info("Processing memory addition in async mode (background task)")
            background_tasks.add_task(_add_memory_background, messages, params)
            processing_time = time.time() - start_time
            logging.info(f"add_memory (async_mode=True) returned in {processing_time:.3f}s")
            return JSONResponse(content={
                "status": "accepted",
                "message": "Memory addition queued for processing",
                "async_mode": True
            })
        else:
            # Sync mode: process immediately
            logging.info("Processing memory addition in sync mode")
            response = _add_memory_sync(messages, params)
            processing_time = time.time() - start_time
            logging.info(f"add_memory (async_mode=False) completed in {processing_time:.3f}s")
            return JSONResponse(content=response)
    except Exception as e:
        processing_time = time.time() - start_time
        logging.exception(f"Error in add_memory after {processing_time:.3f}s:")
        raise HTTPException(status_code=500, detail=str(e))


@api_router.post("/memories/", summary="Create memories (with trailing slash)", include_in_schema=False)
def add_memory_with_slash(memory_create: MemoryCreate, background_tasks: BackgroundTasks):
    """Store new memories - trailing slash version for MemoryClient compatibility."""
    logging.info(f"Received request to /v1/memories/ (trailing slash) - no redirect needed")
    return add_memory(memory_create, background_tasks)


@api_router.get("/memories", summary="Get memories")
def get_all_memories(
    user_id: Optional[str] = None,
    run_id: Optional[str] = None,
    agent_id: Optional[str] = None,
):
    """Retrieve stored memories."""
    if not any([user_id, run_id, agent_id]):
        raise HTTPException(status_code=400, detail="At least one identifier is required.")
    try:
        params = {
            k: v for k, v in {"user_id": user_id, "run_id": run_id, "agent_id": agent_id}.items() if v is not None
        }
        return MEMORY_INSTANCE.get_all(**params)
    except Exception as e:
        logging.exception("Error in get_all_memories:")
        raise HTTPException(status_code=500, detail=str(e))


@api_router.get("/memories/{memory_id}", summary="Get a memory")
def get_memory(memory_id: str):
    """Retrieve a specific memory by ID."""
    try:
        return MEMORY_INSTANCE.get(memory_id)
    except Exception as e:
        logging.exception("Error in get_memory:")
        raise HTTPException(status_code=500, detail=str(e))


@api_router.post("/search", summary="Search memories")
def search_memories(search_req: SearchRequest):
    """Search for memories based on a query."""
    try:
        params = {k: v for k, v in search_req.model_dump().items() if v is not None and k != "query"}
        return MEMORY_INSTANCE.search(query=search_req.query, **params)
    except Exception as e:
        logging.exception("Error in search_memories:")
        raise HTTPException(status_code=500, detail=str(e))


@api_router.put("/memories/{memory_id}", summary="Update a memory")
def update_memory(memory_id: str, updated_memory: Dict[str, Any]):
    """Update an existing memory with new content.
    
    Args:
        memory_id (str): ID of the memory to update
        updated_memory (str): New content to update the memory with
        
    Returns:
        dict: Success message indicating the memory was updated
    """
    try:
        return MEMORY_INSTANCE.update(memory_id=memory_id, data=updated_memory)
    except Exception as e:
        logging.exception("Error in update_memory:")
        raise HTTPException(status_code=500, detail=str(e))


@api_router.get("/memories/{memory_id}/history", summary="Get memory history")
def memory_history(memory_id: str):
    """Retrieve memory history."""
    try:
        return MEMORY_INSTANCE.history(memory_id=memory_id)
    except Exception as e:
        logging.exception("Error in memory_history:")
        raise HTTPException(status_code=500, detail=str(e))


@api_router.delete("/memories/{memory_id}", summary="Delete a memory")
def delete_memory(memory_id: str):
    """Delete a specific memory by ID."""
    try:
        MEMORY_INSTANCE.delete(memory_id=memory_id)
        return {"message": "Memory deleted successfully"}
    except Exception as e:
        logging.exception("Error in delete_memory:")
        raise HTTPException(status_code=500, detail=str(e))


@api_router.delete("/memories", summary="Delete all memories")
def delete_all_memories(
    user_id: Optional[str] = None,
    run_id: Optional[str] = None,
    agent_id: Optional[str] = None,
):
    """Delete all memories for a given identifier."""
    if not any([user_id, run_id, agent_id]):
        raise HTTPException(status_code=400, detail="At least one identifier is required.")
    try:
        params = {
            k: v for k, v in {"user_id": user_id, "run_id": run_id, "agent_id": agent_id}.items() if v is not None
        }
        MEMORY_INSTANCE.delete_all(**params)
        return {"message": "All relevant memories deleted"}
    except Exception as e:
        logging.exception("Error in delete_all_memories:")
        raise HTTPException(status_code=500, detail=str(e))


@api_router.post("/reset", summary="Reset all memories")
def reset_memory():
    """Completely reset stored memories."""
    try:
        MEMORY_INSTANCE.reset()
        return {"message": "All memories reset"}
    except Exception as e:
        logging.exception("Error in reset_memory:")
        raise HTTPException(status_code=500, detail=str(e))


@api_router.get("/ping", summary="Health check endpoint")
def ping():
    """Health check endpoint to verify the API is running."""
    return {"status": "ok", "message": "pong"}


@api_router.get("/health", summary="Health check endpoint")
def health():
    """Health check endpoint with service status."""
    try:
        # Check if memory instance is initialized
        return {
            "status": "healthy",
            "service": "mem0-api",
            "version": "1.0.0"
        }
    except Exception as e:
        return JSONResponse(
            status_code=503,
            content={"status": "unhealthy", "error": str(e)}
        )


# Include the API router with v1 prefix
app.include_router(api_router)

# V2 endpoints for MemoryClient compatibility
@api_router_v2.post("/memories/", summary="Get all memories (v2)")
def get_all_memories_v2(request: GetAllMemoriesRequest):
    """Retrieve stored memories - v2 version (POST method for MemoryClient compatibility)."""
    if not any([request.user_id, request.run_id, request.agent_id]):
        raise HTTPException(status_code=400, detail="At least one identifier is required.")
    try:
        params = {
            k: v for k, v in {
                "user_id": request.user_id,
                "run_id": request.run_id,
                "agent_id": request.agent_id
            }.items() if v is not None
        }
        return MEMORY_INSTANCE.get_all(**params)
    except Exception as e:
        logging.exception("Error in get_all_memories_v2:")
        raise HTTPException(status_code=500, detail=str(e))


@api_router_v2.post("/memories/search/", summary="Search memories (v2)")
def search_memories_v2(search_req: SearchRequest):
    """Search for memories based on a query - v2 version for MemoryClient compatibility."""
    try:
        # Validate query before processing
        if not search_req.query or not search_req.query.strip():
            logging.warning(f"Empty or whitespace-only query provided: {repr(search_req.query)}")
            return {"results": [], "relations": []}
        params = {k: v for k, v in search_req.model_dump().items() if v is not None and k != "query"}
        logging.debug(f"search_memories_v2: query={repr(search_req.query)}, params={params}")
        return MEMORY_INSTANCE.search(query=search_req.query, **params)
    except Exception as e:
        logging.exception("Error in search_memories_v2:")
        raise HTTPException(status_code=500, detail=str(e))


# Include the API router with v2 prefix
app.include_router(api_router_v2)

@app.get("/", summary="Redirect to the OpenAPI documentation", include_in_schema=False)
def home():
    """Redirect to the OpenAPI documentation."""
    return RedirectResponse(url="/docs")
