/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ["./src/**/*.{html,ts}"],
  theme: {
    extend: {
      colors: {
        primary: {
          DEFAULT: '#4F46E5',
          soft: '#EEF2FF'
        },
        accent: {
          DEFAULT: '#14B8A6',
          soft: '#CCFBF1'
        },
        success: '#10B981',
        warning: '#F59E0B',
        danger: '#EF4444',

        surface: '#FFFFFF',
        background: '#F9FAFB',
        border: '#E5E7EB',

        text: {
          main: '#111827',
          muted: '#6B7280'
        }
      },
      borderRadius: {
        xl: '1rem',
        '2xl': '1.25rem'
      }
    }
  },
  plugins: []
};
