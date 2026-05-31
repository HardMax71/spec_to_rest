const CRC_TABLE: Uint32Array = (() => {
  const table = new Uint32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    table[n] = c >>> 0;
  }
  return table;
})();

function crc32(bytes: Uint8Array): number {
  let crc = 0xffffffff;
  for (let i = 0; i < bytes.length; i++) crc = CRC_TABLE[(crc ^ bytes[i]) & 0xff] ^ (crc >>> 8);
  return (crc ^ 0xffffffff) >>> 0;
}

function concatBytes(parts: Uint8Array[]): Uint8Array<ArrayBuffer> {
  let total = 0;
  for (const p of parts) total += p.length;
  const out = new Uint8Array(total);
  let at = 0;
  for (const p of parts) {
    out.set(p, at);
    at += p.length;
  }
  return out;
}

const u16 = (n: number): Uint8Array => new Uint8Array([n & 0xff, (n >>> 8) & 0xff]);
const u32 = (n: number): Uint8Array =>
  new Uint8Array([n & 0xff, (n >>> 8) & 0xff, (n >>> 16) & 0xff, (n >>> 24) & 0xff]);

// Store-only (no compression) ZIP writer. Deflate would only shrink the payload,
// not change correctness, so this stays dependency-free for the playground's
// "download generated files" button.
export function makeZip(files: { path: string; content: string }[]): Blob {
  const encoder = new TextEncoder();
  // General-purpose bit 11 (EFS): marks filenames as UTF-8 so non-ASCII paths
  // decode correctly on extraction.
  const utf8 = u16(0x800);
  const localParts: Uint8Array[] = [];
  const centralParts: Uint8Array[] = [];
  let offset = 0;

  for (const file of files) {
    const name = encoder.encode(file.path);
    const data = encoder.encode(file.content);
    const crc = crc32(data);

    const localHeader = concatBytes([
      u32(0x04034b50),
      u16(20),
      utf8,
      u16(0),
      u16(0),
      u16(0),
      u32(crc),
      u32(data.length),
      u32(data.length),
      u16(name.length),
      u16(0),
      name,
    ]);
    localParts.push(localHeader, data);

    centralParts.push(
      concatBytes([
        u32(0x02014b50),
        u16(20),
        u16(20),
        utf8,
        u16(0),
        u16(0),
        u16(0),
        u32(crc),
        u32(data.length),
        u32(data.length),
        u16(name.length),
        u16(0),
        u16(0),
        u16(0),
        u16(0),
        u32(0),
        u32(offset),
        name,
      ]),
    );
    offset += localHeader.length + data.length;
  }

  const central = concatBytes(centralParts);
  const end = concatBytes([
    u32(0x06054b50),
    u16(0),
    u16(0),
    u16(files.length),
    u16(files.length),
    u32(central.length),
    u32(offset),
    u16(0),
  ]);

  return new Blob([concatBytes(localParts), central, end], { type: "application/zip" });
}
