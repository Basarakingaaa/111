import hashlib
import re
from dataclasses import dataclass

@dataclass(frozen=True)
class Chunk:
    chunk_id: str
    section_path: tuple[str, ...]
    order: int
    content: str
    content_hash: str

def normalize(text: str) -> str:
    return re.sub(r"\s+", " ", text).strip()

def digest(text: str) -> str:
    return hashlib.sha256(normalize(text).encode("utf-8")).hexdigest()

def markdown_chunks(document_id: str, text: str, max_chars: int = 2400) -> list[Chunk]:
    """Create stable, heading-aware chunks. Unchanged sections keep the same IDs."""
    headings: list[str] = []
    sections: list[tuple[tuple[str, ...], list[str]]] = []
    current: list[str] = []
    current_path: tuple[str, ...] = ("document",)
    for line in text.splitlines():
        match = re.match(r"^(#{1,6})\s+(.+?)\s*$", line)
        if match:
            if current:
                sections.append((current_path, current))
                current = []
            level, title = len(match.group(1)), normalize(match.group(2))
            headings[:] = headings[:level-1]
            headings.append(title)
            current_path = tuple(headings)
        else:
            current.append(line)
    if current: sections.append((current_path, current))

    result: list[Chunk] = []
    order = 0
    for path, lines in sections:
        content = normalize("\n".join(lines))
        if not content: continue
        paragraphs = re.split(r"\n\s*\n", "\n".join(lines))
        buffer = ""
        for paragraph in paragraphs:
            paragraph = normalize(paragraph)
            if not paragraph: continue
            if buffer and len(buffer) + len(paragraph) + 2 > max_chars:
                value_hash = digest(buffer)
                result.append(Chunk(f"{document_id}:{'/'.join(path)}:{value_hash[:16]}", path, order, buffer, value_hash))
                order += 1; buffer = paragraph
            else: buffer = f"{buffer}\n\n{paragraph}".strip()
        if buffer:
            value_hash = digest(buffer)
            result.append(Chunk(f"{document_id}:{'/'.join(path)}:{value_hash[:16]}", path, order, buffer, value_hash)); order += 1
    return result

def changed_chunks(old: list[Chunk], new: list[Chunk]) -> tuple[list[Chunk], list[str]]:
    old_by_id, new_by_id = {c.chunk_id:c for c in old}, {c.chunk_id:c for c in new}
    upserts = [chunk for key, chunk in new_by_id.items() if key not in old_by_id]
    deletes = [key for key in old_by_id if key not in new_by_id]
    return upserts, deletes

