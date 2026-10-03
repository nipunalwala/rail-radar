#!/usr/bin/env bash
# Builds the debug app, runs the unit tests and prints a short summary.
# Usage: tools/build.sh [extra gradle arguments]
cd "$(dirname "$0")/.." || exit 1
log="${TEMP:-/tmp}/tnm_build_sh.log"
./gradlew.bat testDebugUnitTest assembleDebug --console=plain "$@" > "$log" 2>&1
echo "gradle exit=$?"
grep -E "^e: |FAILED|BUILD |error:" "$log" | sed "s|file:///C:/Coding/rail-radar/app/src/||" | head -30
node -e "
const fs=require('fs');const d='app/build/test-results/testDebugUnitTest/';let t=0,f=0;
for(const x of fs.readdirSync(d).filter(n=>n.endsWith('.xml'))){const s=fs.readFileSync(d+x,'utf8');
t+=+s.match(/tests=\"(\d+)\"/)[1];f+=+s.match(/failures=\"(\d+)\"/)[1]+ +s.match(/errors=\"(\d+)\"/)[1]}
console.log('tests',t,'failed',f)"
