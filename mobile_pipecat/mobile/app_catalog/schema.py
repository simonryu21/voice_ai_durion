"""Pydantic schema for the app catalog.

Mirrors `app-concierge-agent-design.md` §4.1. The catalog is a set of YAML files
(one per category) under `mobile/app_catalog/` that describe how to invoke each
known app via Android/iOS execution paths.
"""

from typing import Any, Literal, Optional

from pydantic import BaseModel, ConfigDict, Field, model_validator

ParamType = Literal["string", "number", "boolean"]
ExecutionType = Literal[
    "applink",  # Android App Link (https://...)
    "deeplink",  # Custom scheme (e.g., nmap://)
    "intent",  # Android Intent action
    "universal_link",  # iOS Universal Link
    "scheme",  # iOS custom scheme
]
Platform = Literal["android", "ios"]


class CatalogParam(BaseModel):
    """A parameter that can be extracted from user utterances and injected into execution paths."""

    model_config = ConfigDict(extra="forbid")

    name: str
    type: ParamType
    required: bool = False
    description: Optional[str] = None


class ExecutionPath(BaseModel):
    """One way to invoke an app — applink, deeplink, intent, etc.

    `priority` is ascending: 1 is tried first. The catalog author orders them
    so that more deeply-linked paths (e.g., applink with full state) come before
    less specific ones (e.g., a bare intent that just opens the home screen).
    """

    model_config = ConfigDict(extra="forbid")

    type: ExecutionType
    priority: int = Field(ge=1)
    template: Optional[str] = None  # for applink/deeplink/scheme/universal_link
    action: Optional[str] = None  # for intent: e.g., "android.intent.action.VIEW"
    uri_template: Optional[str] = None  # for intent: data URI template
    extras: Optional[dict[str, str]] = None  # for intent: extra k/v pairs

    @model_validator(mode="after")
    def _check_required_fields(self) -> "ExecutionPath":
        if self.type == "intent":
            if not self.action:
                raise ValueError("intent execution path requires `action`")
        else:
            if not self.template:
                raise ValueError(f"{self.type} execution path requires `template`")
        return self


class CatalogApp(BaseModel):
    """A single app entry in the catalog."""

    model_config = ConfigDict(extra="forbid")

    app_id: str  # unique within the catalog; also used as tool/handle name
    display_name: str
    package_name: str  # Android package, used to match against inventory
    bundle_id: Optional[str] = None  # iOS
    popularity_rank: int = Field(ge=1)
    use_when: str  # 1-2 sentence guide for when this app is appropriate
    examples: list[str] = Field(default_factory=list)
    parameters: list[CatalogParam] = Field(default_factory=list)
    execution: dict[Platform, list[ExecutionPath]] = Field(default_factory=dict)
    fallback: Optional[dict[str, Any]] = None  # reserved for future expansion

    @model_validator(mode="after")
    def _check_execution_present(self) -> "CatalogApp":
        if not self.execution:
            raise ValueError(f"app `{self.app_id}` has no execution paths")
        for platform, paths in self.execution.items():
            if not paths:
                raise ValueError(
                    f"app `{self.app_id}` has empty execution list for platform `{platform}`"
                )
            priorities = [p.priority for p in paths]
            if len(set(priorities)) != len(priorities):
                raise ValueError(
                    f"app `{self.app_id}` has duplicate priorities for platform `{platform}`: "
                    f"{priorities}"
                )
        return self


class CatalogCategory(BaseModel):
    """One catalog file represents one category, e.g. `navigation` or `social_chat`."""

    model_config = ConfigDict(extra="forbid")

    category: str  # machine-readable id, matches the file name
    display_name: str  # human-readable
    apps: list[CatalogApp]

    @model_validator(mode="after")
    def _check_apps_unique(self) -> "CatalogCategory":
        ids = [a.app_id for a in self.apps]
        if len(set(ids)) != len(ids):
            raise ValueError(f"category `{self.category}` has duplicate app_ids: {ids}")
        return self
