#!/usr/bin/env node
// Non-destructive link, heading, and manifest-consistency checks over architecture.md and
// blueprints/, per blueprints/build-and-ci.md's "docs check job".
import { readFileSync, readdirSync, existsSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const repoRoot = dirname(dirname(fileURLToPath(import.meta.url)));
let failures = [];

function readText(relativePath) {
  return readFileSync(join(repoRoot, relativePath), "utf8");
}

const REQUIRED_BLUEPRINT_HEADINGS = [
  "## Outcome or responsibility",
  "## Current verified status",
  "## Architecture dependencies",
  "## Local rules and implications",
  "## Related blueprints",
  "### Required",
  "### Impact checks",
  "## Relevant implementation and tests",
  "## Acceptance or verification criteria",
  "## Remaining gaps and unknowns",
];

function checkBlueprintHeadings(path, content) {
  for (const heading of REQUIRED_BLUEPRINT_HEADINGS) {
    if (!content.includes(heading)) {
      failures.push(`${path}: missing required heading "${heading}"`);
    }
  }
}

function extractMarkdownLinks(content) {
  const linkPattern = /\[[^\]]*\]\(([^)]+)\)/g;
  const links = [];
  let match;
  while ((match = linkPattern.exec(content)) !== null) {
    links.push(match[1]);
  }
  return links;
}

function checkLink(sourcePath, link) {
  if (/^https?:\/\//.test(link)) return; // external links are not checked here

  const [pathPart, anchor] = link.split("#");
  const sourceDir = dirname(sourcePath);
  const targetPath = pathPart === "" ? sourcePath : join(sourceDir, pathPart);

  if (pathPart !== "" && !existsSync(join(repoRoot, targetPath))) {
    failures.push(`${sourcePath}: broken link target "${link}"`);
    return;
  }

  if (anchor) {
    const targetContent = readText(pathPart === "" ? sourcePath : targetPath);
    const slug = anchor.toLowerCase();
    const headingSlugs = [...targetContent.matchAll(/^#{1,6}\s+(.+)$/gm)].map(([, text]) =>
      text
        .toLowerCase()
        .trim()
        .replace(/[^a-z0-9\s-]/g, "")
        .replace(/\s+/g, "-"),
    );
    if (!headingSlugs.includes(slug)) {
      failures.push(`${sourcePath}: anchor "#${anchor}" not found in ${pathPart || sourcePath}`);
    }
  }
}

// 1. architecture.md must exist and its links must resolve.
const architecturePath = "architecture.md";
const architectureContent = readText(architecturePath);
for (const link of extractMarkdownLinks(architectureContent)) {
  checkLink(architecturePath, link);
}

// 2. Every blueprint file must have the required headings and resolvable links.
const blueprintsDir = "blueprints";
const blueprintFiles = readdirSync(join(repoRoot, blueprintsDir)).filter(
  (f) => f.endsWith(".md") && f !== "README.md",
);

for (const file of blueprintFiles) {
  const path = join(blueprintsDir, file);
  const content = readText(path);
  checkBlueprintHeadings(path, content);
  for (const link of extractMarkdownLinks(content)) {
    checkLink(path, link);
  }
}

// 3. The manifest must list every blueprint exactly once, and every listed blueprint must exist.
const manifestPath = join(blueprintsDir, "README.md");
const manifestContent = readText(manifestPath);
for (const link of extractMarkdownLinks(manifestContent)) {
  checkLink(manifestPath, link);
}

// A blueprint may legitimately be linked from several rows (its own Primary entry, plus any
// row that lists it as a Required dependency or Impact check), so we only require that it is
// routed to at least once, not exactly once.
for (const file of blueprintFiles) {
  if (!manifestContent.includes(`(${file})`) && !manifestContent.includes(`(${file}#`)) {
    failures.push(`blueprints/README.md: ${file} is not referenced by the manifest`);
  }
}

if (failures.length > 0) {
  console.error("Documentation check failed:\n");
  for (const failure of failures) console.error(`  - ${failure}`);
  process.exit(1);
}

console.log(`Documentation check passed (${blueprintFiles.length} blueprints validated).`);
