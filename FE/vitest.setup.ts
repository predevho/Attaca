import { vi } from 'vitest';
import '@testing-library/jest-dom/vitest';

// Mock 'server-only' for testing (Next.js built-in package)
vi.mock('server-only', () => ({}));
