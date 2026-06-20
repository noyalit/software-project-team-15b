/**
 * Minimal, dependency-free Code 128 (subset B) barcode renderer.
 *
 * Encodes an ASCII string (values 32-126) into a Code 128-B barcode and
 * draws it as an SVG. This is sufficient for rendering ticket codes such as
 * the external ticketing-service id and is intentionally self-contained so
 * the UI does not pull in an extra runtime dependency.
 */

// The 107 Code 128 patterns. Each pattern is the run-length of bars/spaces,
// starting with a bar. Index matches the Code 128 value.
const CODE128_PATTERNS = [
  '212222', '222122', '222221', '121223', '121322', '131222', '122213',
  '122312', '132212', '221213', '221312', '231212', '112232', '122132',
  '122231', '113222', '123122', '123221', '223211', '221132', '221231',
  '213212', '223112', '312131', '311222', '321122', '321221', '312212',
  '322112', '322211', '212123', '212321', '232121', '111323', '131123',
  '131321', '112313', '132113', '132311', '211313', '231113', '231311',
  '112133', '112331', '132131', '113123', '113321', '133121', '313121',
  '211331', '231131', '213113', '213311', '213131', '311123', '311321',
  '331121', '312113', '312311', '332111', '314111', '221411', '431111',
  '111224', '111422', '121124', '121421', '141122', '141221', '112214',
  '112412', '122114', '122411', '142112', '142211', '241211', '221114',
  '413111', '241112', '134111', '111242', '121142', '121241', '114212',
  '124112', '124211', '411212', '421112', '421211', '212141', '214121',
  '412121', '111143', '111341', '131141', '114113', '114311', '411113',
  '411311', '113141', '114131', '311141', '113111', '113311', '211231',
  '211130', '211329',
];

const START_B = 104;
const STOP = 106;

function encodeCode128B(value: string): number[] {
  const codes: number[] = [START_B];
  let checksum = START_B;

  for (let i = 0; i < value.length; i++) {
    const charCode = value.charCodeAt(i);
    // Code 128-B covers ASCII 32..126 -> values 0..94.
    const val = charCode >= 32 && charCode <= 126 ? charCode - 32 : 0;
    codes.push(val);
    checksum += val * (i + 1);
  }

  codes.push(checksum % 103);
  codes.push(STOP);
  return codes;
}

type BarcodeProps = {
  value: string;
  height?: number;
  barWidth?: number;
  className?: string;
};

export default function Barcode({
  value,
  height = 64,
  barWidth = 2,
  className,
}: BarcodeProps) {
  const codes = encodeCode128B(value);

  // Build the bar geometry. Even run indexes are bars, odd are spaces.
  const bars: Array<{ x: number; width: number }> = [];
  let x = 0;
  for (const code of codes) {
    const pattern = CODE128_PATTERNS[code];
    for (let i = 0; i < pattern.length; i++) {
      const run = Number(pattern[i]) * barWidth;
      if (i % 2 === 0) {
        bars.push({ x, width: run });
      }
      x += run;
    }
  }

  const totalWidth = x;

  return (
    <svg
      className={className}
      width={totalWidth}
      height={height}
      viewBox={`0 0 ${totalWidth} ${height}`}
      role="img"
      aria-label={`Barcode for ${value}`}
      preserveAspectRatio="xMidYMid meet"
    >
      <rect x={0} y={0} width={totalWidth} height={height} fill="#ffffff" />
      {bars.map((bar, i) => (
        <rect
          key={i}
          x={bar.x}
          y={0}
          width={bar.width}
          height={height}
          fill="#0f172a"
        />
      ))}
    </svg>
  );
}
