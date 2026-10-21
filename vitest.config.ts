export default {
  oxc: {
    tsconfig: false,
    tsconfigRaw: {
      compilerOptions: {
        target: 'ES2022',
        module: 'ESNext',
        moduleResolution: 'Bundler',
        baseUrl: '.',
        strict: true,
        paths: { '@/*': ['./src/*'] },
      },
    },
  },
  test: {
    environment: 'node',
    server: { deps: { external: ['expo'] } },
  },
};