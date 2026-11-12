#!/usr/bin/env bash
# Slow, polite fetcher for LiveHarness: fetches each query of <dir>/plan.tsv into <dir>/raw/NN.json (one every ~4 s).
set -u
D=$1; UA='Mozilla/5.0 (Linux; Android 15; SM-S938B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36'
CAT=${CATALOG:-}
mkdir -p "$D/raw"; rm -f "$D/c.txt"
curl -s -c "$D/c.txt" -b "$D/c.txt" -A "$UA" -o /dev/null -w "home %{http_code}\n" https://www.vinted.fr/
i=0
while IFS=$'\t' read -r q order; do
  [ -z "$q" ] && continue
  i=$((i+1)); f=$(printf "%s/raw/%02d.json" "$D" $i)
  enc=$(python3 -c "import urllib.parse,sys;print(urllib.parse.quote(sys.argv[1]))" "$q")
  url="https://api.vinted.fr/svc-catalogue/items?search_text=$enc&page=1&per_page=96&order=$order${CAT:+&attribute_ids%5Bcatalog%5D=$CAT}"
  for attempt in 1 2 3; do
    code=$(curl -s -b "$D/c.txt" -A "$UA" -H 'Locale: fr-FR' -H 'Accept-Language: fr-FR,fr;q=0.9' -H 'Accept: application/json, text/plain, */*' \
      -H 'Origin: https://www.vinted.fr' -H 'Referer: https://www.vinted.fr/' -H 'Platform: web' -o "$f" -w '%{http_code}' "$url")
    [ "$code" = 200 ] && break
    sleep $((attempt * 8))
  done
  n=$(python3 -c "import json,sys;print(len(json.load(open(sys.argv[1]))['items']))" "$f" 2>/dev/null || echo ERR)
  echo "$code $order $n  $q"
  sleep 4
done < "$D/plan.tsv"
