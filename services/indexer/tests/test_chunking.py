from app.chunking import markdown_chunks, changed_chunks

def test_unchanged_section_keeps_stable_id():
    old = markdown_chunks("doc-1", "# A\nunchanged\n\n# B\nold")
    new = markdown_chunks("doc-1", "# A\nunchanged\n\n# B\nnew")
    assert old[0].chunk_id == new[0].chunk_id
    upserts, deletes = changed_chunks(old, new)
    assert len(upserts) == 1
    assert len(deletes) == 1

def test_heading_path_is_preserved():
    chunks = markdown_chunks("doc-2", "# Deploy\n## Database\nRun migration")
    assert chunks[0].section_path == ("Deploy", "Database")

