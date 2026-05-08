const { setGlobalOptions } = require("firebase-functions");

const amenities = require("./amenitiesManagement");
const account = require("./accountManagement");
const terminalMap = require("./terminalMapManagement");

setGlobalOptions({ maxInstances: 10 });

Object.assign(exports, amenities, account, terminalMap);
