/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ["./src/**/*.{html,ts}"],
  theme: {
    extend: {
      colors: {
        primary: {
          DEFAULT: '#4F46E5',
          soft: '#EEF2FF',
          dark: '#3730A3',
          light: '#818CF8'
        },
        accent: {
          DEFAULT: '#14B8A6',
          soft: '#CCFBF1'
        },
        success: '#10B981',
        warning: '#F59E0B',
        danger: '#EF4444',

        surface: '#FFFFFF',
        background: '#F8FAFC',
        border: '#E2E8F0',

        text: {
          main: '#0F172A',
          muted: '#64748B'
        }
      },
      borderRadius: {
        xl: '1rem',
        '2xl': '1.25rem',
        '3xl': '1.5rem'
      },
      boxShadow: {
        'soft': '0 2px 15px -3px rgba(0, 0, 0, 0.07), 0 10px 20px -2px rgba(0, 0, 0, 0.04)',
        'card': '0 1px 3px 0 rgba(0, 0, 0, 0.1), 0 1px 2px -1px rgba(0, 0, 0, 0.1)',
        'elevated': '0 10px 40px -10px rgba(79, 70, 229, 0.2)'
      }
    }
  },
  plugins: []
};
