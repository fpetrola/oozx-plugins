"""The served base as SQL D1 imports: no transaction of its own, one row per statement, and the word index D1 builds
itself afterwards (FTS5 keeps tables of its own that a dump cannot carry)."""
import sqlite3
import sys

served, into = sys.argv[1], sys.argv[2]
with sqlite3.connect(served) as db, open(into, 'w', encoding='utf-8') as out:
    for line in db.iterdump():
        if line not in ('BEGIN TRANSACTION;', 'COMMIT;'):
            out.write(line + '\n')
    out.write('CREATE INDEX hits_by_machine ON hits (machine, votes DESC);\n')
    out.write('CREATE INDEX hits_by_genre ON hits (genre, votes DESC);\n')
    out.write('CREATE INDEX hits_by_year ON hits (year, votes DESC);\n')
    out.write("CREATE VIRTUAL TABLE found USING fts5(names, content='hits', content_rowid='id');\n")
    out.write("INSERT INTO found(found) VALUES ('rebuild');\n")
