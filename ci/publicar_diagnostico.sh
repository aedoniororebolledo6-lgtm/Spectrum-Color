#!/usr/bin/env bash
# Sube report.txt y las capturas de ci/smoke.sh al release "diagnostico" (prerelease: no afecta a "latest").
set +e
[ -f report.txt ] || echo "No se generó informe" > report.txt
{ echo "Rama: ${GITHUB_REF_NAME:-?} · commit ${GITHUB_SHA:0:7} · ejecución #${GITHUB_RUN_NUMBER:-?}"; echo; cat report.txt; } \
  | tr -cd '\11\12\15\40-\176\200-\377' | head -c 60000 > notes.txt
gh release delete diagnostico --yes --cleanup-tag || true
gh release create diagnostico --title "Diagnóstico" --notes-file notes.txt --prerelease || echo "No se pudo crear el release"
for f in report.txt *.png; do
  [ -f "$f" ] && { gh release upload diagnostico "$f" --clobber || echo "No se pudo subir $f"; }
done
exit 0
