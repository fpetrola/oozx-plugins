// The ZXInfo web service's own questions, answered from ZXDB: every entry is already written in the service's JSON
// (Zxdb.served builds them), so this only finds rows and hands them out.

const HEADERS = { 'content-type': 'application/json', 'access-control-allow-origin': '*', 'cache-control': 'public, max-age=3600' };
const MOST = 1000;

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const path = url.pathname.replace(/\/+$/, '');
    const asked = url.searchParams;
    let game;
    if ((game = path.match(/^\/v3\/games\/(\d+)$/))) {
      const row = await env.DB.prepare('SELECT hit FROM hits WHERE id = ?').bind(Number(game[1])).first();
      return row ? new Response(row.hit, { headers: HEADERS }) : answer({ found: false }, 404);
    }
    if (path === '/v3/metadata') {
      const row = await env.DB.prepare('SELECT json FROM metadata').first();
      return new Response(row.json, { headers: HEADERS });
    }
    if (path === '/v3/search') return answer(await search(env.DB, asked));
    return answer({ error: 'Not something this service answers: ' + path }, 404);
  },
};

/** The entries with every word asked in a name they went by, narrowed as asked, the best known first. */
async function search(db, asked) {
  const words = (asked.get('query') || '').toLowerCase().match(/[\p{L}\p{N}]+/gu) || [];
  const size = Math.min(MOST, Math.max(1, Number(asked.get('size')) || 10));
  const from = Math.max(0, Number(asked.get('offset')) || 0);
  const where = [], values = [];
  if (words.length) {
    where.push('id IN (SELECT rowid FROM found WHERE found MATCH ?)');
    values.push(words.map((word) => '"' + word + '"*').join(' '));
  }
  for (const [parameter, column] of [['machinetype', 'machine'], ['genretype', 'genre'], ['year', 'year']]) {
    if (asked.get(parameter)) {
      where.push(column + ' = ?');
      values.push(column === 'year' ? Number(asked.get(parameter)) : asked.get(parameter));
    }
  }
  const sql = 'SELECT hit FROM hits' + (where.length ? ' WHERE ' + where.join(' AND ') : '')
    + ' ORDER BY ' + (words.length ? 'lower(title) LIKE ? DESC, ' : '') + 'votes DESC, title LIMIT ' + size + ' OFFSET ' + from;
  if (words.length) values.push(words.join(' ') + '%');
  const { results } = await db.prepare(sql).bind(...values).all();
  const hits = results.map((row) => JSON.parse(row.hit));
  return { took: 0, timed_out: false, hits: { total: { value: hits.length, relation: 'eq' }, hits } };
}

function answer(body, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: HEADERS });
}
