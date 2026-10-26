const { withProjectBuildGradle } = require('expo/config-plugins');

const MARKER = "force 'commons-io:commons-io:2.16.1'";
const BLOCK = `
allprojects {
  configurations.all {
    resolutionStrategy {
      ${MARKER}
    }
  }
}
`;

module.exports = function withCommonsIoVersion(config) {
  return withProjectBuildGradle(config, (builder) => {
    if (builder.modResults.contents.includes(MARKER)) return builder;
    builder.modResults.contents += BLOCK;
    return builder;
  });
};
