/** @type {import('tailwindcss').Config} */
module.exports = {
  darkMode: 'class',
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
        },

        // Dark mode colors
        'dark-surface': '#1E293B',
        'dark-background': '#0F172A',
        'dark-border': '#334155',
        'dark-text': {
          main: '#F1F5F9',
          muted: '#94A3B8'
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
        'elevated': '0 10px 40px -10px rgba(79, 70, 229, 0.2)',
        'dark-soft': '0 2px 15px -3px rgba(0, 0, 0, 0.3), 0 10px 20px -2px rgba(0, 0, 0, 0.2)',
        'dark-card': '0 1px 3px 0 rgba(0, 0, 0, 0.3), 0 1px 2px -1px rgba(0, 0, 0, 0.2)',
        'dark-elevated': '0 10px 40px -10px rgba(79, 70, 229, 0.4)'
      }
    }
  },
  plugins: []
};
