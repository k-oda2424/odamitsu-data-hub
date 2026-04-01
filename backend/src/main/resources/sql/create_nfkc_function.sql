-- 全角・半角を統一して検索するためのNFKC正規化関数
-- JPA CriteriaBuilder から呼び出すためのラッパー
CREATE OR REPLACE FUNCTION nfkc(text) RETURNS text AS $$
  SELECT normalize($1, NFKC)
$$ LANGUAGE sql IMMUTABLE STRICT;
