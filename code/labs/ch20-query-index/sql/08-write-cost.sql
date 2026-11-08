-- インデックスは書き込みのたびに更新される。同じ 30 万行の INSERT を、インデックスの数を変えて比べる
\timing on
CREATE TEMP TABLE w0 (a bigint, b bigint, c bigint, d text);
CREATE TEMP TABLE w3 (a bigint, b bigint, c bigint, d text);
CREATE INDEX w3_a ON w3 (a);
CREATE INDEX w3_b ON w3 (b);
CREATE INDEX w3_c ON w3 (c, a);
\echo --- インデックスなし
INSERT INTO w0 SELECT g, g % 1000, (g * 7919) % 100003, md5(g::text) FROM generate_series(1, 300000) g;
\echo --- インデックス3本
INSERT INTO w3 SELECT g, g % 1000, (g * 7919) % 100003, md5(g::text) FROM generate_series(1, 300000) g;
