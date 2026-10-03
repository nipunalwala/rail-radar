// Writes src/codes.json: the station codes the app can ask for, taken from the
// app's own station list. Run after stations.json changes: npm run codes
import { readFileSync, writeFileSync } from "node:fs";

const stations = JSON.parse(readFileSync(new URL("../../app/src/main/assets/stations.json", import.meta.url), "utf8"));
const codes = [...new Set(stations.flatMap((station) => station.providerCodes))].sort();
writeFileSync(new URL("./src/codes.json", import.meta.url), JSON.stringify(codes) + "\n");
console.log(`${codes.length} codes`);
