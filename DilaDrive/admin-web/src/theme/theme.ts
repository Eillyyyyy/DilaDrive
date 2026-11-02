import { createTheme } from '@mui/material/styles';

/** Design tokens from docs/DILADRIVE_BLUEPRINT.md §4 (maroon + cream + white cards). */
export const tokens = {
  primary900: '#3D0606', // sidebar background, pressed state
  primary700: '#5B0A0A', // primary buttons, app bar
  primary500: '#8B1A1A', // hover, links, icons
  primary100: '#F3E3E3', // selected row, subtle badge background
  bgCream: '#FAF6F0', // page background
  surface: '#FFFFFF', // cards, modals, inputs
  border: '#E8E0D6',
  accentGold: '#C9A24B',
  textPrimary: '#2B1B1B',
  textMuted: '#7A6A6A',
  success: '#2E7D32',
  warning: '#ED8B00',
  danger: '#C62828',
  info: '#1565C0',
} as const;

export const theme = createTheme({
  palette: {
    primary: { main: tokens.primary700, dark: tokens.primary900, light: tokens.primary500, contrastText: '#FFFFFF' },
    secondary: { main: tokens.accentGold },
    success: { main: tokens.success },
    warning: { main: tokens.warning },
    error: { main: tokens.danger },
    info: { main: tokens.info },
    background: { default: tokens.bgCream, paper: tokens.surface },
    text: { primary: tokens.textPrimary, secondary: tokens.textMuted },
    divider: tokens.border,
  },
  shape: { borderRadius: 8 },
  typography: {
    fontFamily: '"Inter", "Helvetica", "Arial", sans-serif',
    h1: { fontFamily: '"Poppins", "Inter", sans-serif', fontWeight: 600, fontSize: 32 },
    h2: { fontFamily: '"Poppins", "Inter", sans-serif', fontWeight: 600, fontSize: 24 },
    h3: { fontFamily: '"Poppins", "Inter", sans-serif', fontWeight: 600, fontSize: 20 },
    h4: { fontFamily: '"Poppins", "Inter", sans-serif', fontWeight: 600, fontSize: 16 },
    body1: { fontSize: 14 },
    body2: { fontSize: 12 },
    button: { textTransform: 'none', fontWeight: 600 },
  },
  components: {
    MuiButton: { defaultProps: { disableElevation: true } },
    MuiCard: {
      styleOverrides: {
        root: { borderRadius: 12, boxShadow: '0 1px 3px rgba(61,6,6,.08)', border: `1px solid ${tokens.border}` },
      },
    },
    MuiTableHead: {
      styleOverrides: { root: { backgroundColor: tokens.primary700, '& .MuiTableCell-head': { color: '#fff', fontWeight: 600 } } },
    },
    MuiDrawer: {
      styleOverrides: { paper: { backgroundColor: tokens.primary900, color: '#fff', width: 240 } },
    },
  },
});

/** Badge colours per status enum (blueprint §4.5). */
export const statusColor: Record<string, string> = {
  PENDING: tokens.warning,
  CONFIRMED: tokens.info,
  ASSIGNED: tokens.info,
  IN_PROGRESS: tokens.primary500,
  COMPLETED: tokens.success,
  CANCELLED: tokens.danger,
  REJECTED: tokens.danger,
  PENDING_VERIFICATION: tokens.warning,
  VERIFIED: tokens.success,
};
