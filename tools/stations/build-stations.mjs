// Builds app/src/main/assets/stations.json.
//
// Usage: node tools/stations/build-stations.mjs <osm.json> <railradar-stations.json>
//
//   osm.json                 Overpass API result for railway=station|halt in the
//                            Mumbai region (coordinates, © OpenStreetMap contributors, ODbL)
//   railradar-stations.json  GET /v1/lookup/stations (code -> name), used only to
//                            confirm that every code exists at the provider
//
// Line membership is listed here by hand, in order along each line.

import { readFileSync, writeFileSync } from "node:fs";

const WESTERN = [
  ["Churchgate", "CCG"], ["Marine Lines", "MEL"], ["Charni Road", "CYR"], ["Grant Road", "GTR"],
  ["Mumbai Central", "BCL"], ["Mahalaxmi", "MX"], ["Lower Parel", "PL"], ["Prabhadevi", "PBHD"],
  ["Dadar", "DDR"], ["Matunga Road", "MRU"], ["Mahim", "MM"], ["Bandra", "BA"],
  ["Khar Road", "KHAR"], ["Santacruz", "STC"], ["Vile Parle", "VLP"], ["Andheri", "ADH"],
  ["Jogeshwari", "JOS"], ["Ram Mandir", "RMAR"], ["Goregaon", "GMN"], ["Malad", "MDD"],
  ["Kandivali", "KILE"], ["Borivali", "BVI"], ["Dahisar", "DIC"], ["Mira Road", "MIRA"],
  ["Bhayandar", "BYR"], ["Naigaon", "NIG"], ["Vasai Road", "BSR"], ["Nallasopara", "NSP"],
  ["Virar", "VR"], ["Vaitarna", "VTN"], ["Saphale", "SAH"], ["Kelve Road", "KLV"],
  ["Palghar", "PLG"], ["Umroli", "UOI"], ["Boisar", "BOR"], ["Vangaon", "VGN"],
  ["Dahanu Road", "DRD"],
];

const CENTRAL = [
  ["Mumbai CSMT", "CSMT"], ["Masjid", "MSD"], ["Sandhurst Road", "SNRD"], ["Byculla", "BY"],
  ["Chinchpokli", "CHG"], ["Currey Road", "CRD"], ["Parel", "PR"], ["Dadar", "DR"],
  ["Matunga", "MTN"], ["Sion", "SIN"], ["Kurla", "CLA"], ["Vidyavihar", "VVH"],
  ["Ghatkopar", "GC"], ["Vikhroli", "VK"], ["Kanjurmarg", "KJRD"], ["Bhandup", "BND"],
  ["Nahur", "NHU"], ["Mulund", "MLND"], ["Thane", "TNA"], ["Kalwa", "KLVA"],
  ["Mumbra", "MBQ"], ["Diva", "DIVA"], ["Kopar", "KOPR"], ["Dombivli", "DI"],
  ["Thakurli", "THK"], ["Kalyan", "KYN"],
  // Kalyan - Kasara
  ["Shahad", "SHAD"], ["Ambivli", "ABY"], ["Titwala", "TLA"], ["Khadavli", "KDV"],
  ["Vasind", "VSD"], ["Asangaon", "ASO"], ["Atgaon", "ATG"], ["Thansit", "THS"],
  ["Khardi", "KE"], ["Umbermali", "OMB"], ["Kasara", "KSRA"],
  // Kalyan - Khopoli
  ["Vithalwadi", "VLDI"], ["Ulhasnagar", "ULNR"], ["Ambarnath", "ABH"], ["Badlapur", "BUD"],
  ["Vangani", "VGI"], ["Shelu", "SHLU"], ["Neral", "NRL"], ["Bhivpuri Road", "BVS"],
  ["Karjat", "KJT"], ["Palasdari", "PDI"], ["Kelavli", "KLY"], ["Dolavli", "DLV"],
  ["Lowjee", "LWJ"], ["Khopoli", "KHPI"],
];

const HARBOUR = [
  ["Mumbai CSMT", "CSMT"], ["Masjid", "MSD"], ["Sandhurst Road", "SNRD"],
  ["Dockyard Road", "DKRD"], ["Reay Road", "RRD"], ["Cotton Green", "CTGN"], ["Sewri", "SVE"],
  ["Vadala Road", "VDLR"], ["GTB Nagar", "GTBN"], ["Chunabhatti", "CHF"], ["Kurla", "CLA"],
  ["Tilak Nagar", "TKNG"], ["Chembur", "CMBR"], ["Govandi", "GV"], ["Mankhurd", "MNKD"],
  ["Vashi", "VSH"], ["Sanpada", "SNCR"], ["Juinagar", "JNJ"], ["Nerul", "NEU"],
  ["Seawoods Darave", "SWDV"], ["Belapur CBD", "BEPR"], ["Kharghar", "KHAG"],
  ["Mansarovar", "MANR"], ["Khandeshwar", "KNDS"], ["Panvel", "PNVL"],
  // Vadala Road - Goregaon
  ["King's Circle", "KCE"], ["Mahim", "MM"], ["Bandra", "BA"], ["Khar Road", "KHAR"],
  ["Santacruz", "STC"], ["Vile Parle", "VLP"], ["Andheri", "ADH"], ["Jogeshwari", "JOS"],
  ["Ram Mandir", "RMAR"], ["Goregaon", "GMN"],
];

const LINES = { WESTERN, CENTRAL, HARBOUR };

// Provider codes whose OpenStreetMap station carries a different ref (the
// mainline code) or no ref at all.
const OSM_REF = { BCL: "MMCT" };
const OSM_NAME = { VDLR: "Vadala Road" };

const [osmPath, rrPath, outPath = "app/src/main/assets/stations.json"] = process.argv.slice(2);
if (!osmPath || !rrPath) {
  console.error("usage: node build-stations.mjs <osm.json> <railradar-stations.json> [out.json]");
  process.exit(2);
}

const rr = JSON.parse(readFileSync(rrPath, "utf8")).data;
const osmByRef = new Map();
const osmByName = new Map();
for (const e of JSON.parse(readFileSync(osmPath, "utf8")).elements) {
  if (["subway", "monorail"].includes(e.tags?.station)) continue;
  const point = { name: e.tags.name, lat: e.lat ?? e.center.lat, lng: e.lon ?? e.center.lon };
  if (e.tags.ref) osmByRef.set(e.tags.ref, point);
  if (e.tags.name) osmByName.set(e.tags.name, point);
}
for (const [code, ref] of Object.entries(OSM_REF)) osmByRef.set(code, osmByRef.get(ref));
for (const [code, name] of Object.entries(OSM_NAME)) osmByRef.set(code, osmByName.get(name));

const problems = [];
const stations = new Map();
for (const [line, entries] of Object.entries(LINES)) {
  for (const [name, code] of entries) {
    if (!(code in rr)) problems.push(`${code} (${name}): not in RailRadar directory`);
    if (!osmByRef.get(code)) problems.push(`${code} (${name}): no OpenStreetMap station with this ref`);
    const id = name.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "");
    const s = stations.get(id) ?? { id, name, lines: [], providerCodes: [] };
    if (!s.lines.includes(line)) s.lines.push(line);
    if (!s.providerCodes.includes(code)) s.providerCodes.push(code);
    stations.set(id, s);
  }
}

const round = (n) => Math.round(n * 1e6) / 1e6;
const out = [];
for (const s of stations.values()) {
  const points = s.providerCodes.map((c) => osmByRef.get(c)).filter(Boolean);
  if (points.length === 0) continue;
  // An interchange with one code per railway is centred between its halves.
  out.push({
    id: s.id,
    name: s.name,
    lat: round(points.reduce((a, p) => a + p.lat, 0) / points.length),
    lng: round(points.reduce((a, p) => a + p.lng, 0) / points.length),
    lines: s.lines,
    providerCodes: s.providerCodes,
  });
}

for (const [line, entries] of Object.entries(LINES)) console.log(`${line}: ${entries.length} stations`);
console.log(`unique stations: ${stations.size}, written: ${out.length}`);
if (process.argv.includes("--names")) {
  for (const s of stations.values()) {
    for (const c of s.providerCodes) console.log(`${c.padEnd(5)} ours="${s.name}" railradar="${rr[c]}" osm="${osmByRef.get(c)?.name}"`);
  }
}
if (problems.length) {
  console.log(`\n${problems.length} problem(s):`);
  for (const p of problems) console.log("  " + p);
  process.exit(1);
}
writeFileSync(outPath, JSON.stringify(out, null, 1) + "\n");
console.log(`wrote ${outPath}`);
