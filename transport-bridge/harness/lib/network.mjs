import net from 'node:net';

export function required(name) {
  const value = process.env[name];
  if (!value) throw new Error(`${name} is required`);
  return value;
}

export function hex(value, expectedBytes) {
  if (!new RegExp(`^[0-9a-f]{${expectedBytes * 2}}$`, 'i').test(value)) {
    throw new Error(`Expected ${expectedBytes} bytes of hexadecimal`);
  }
  return new Uint8Array(Buffer.from(value, 'hex'));
}

export function toHex(value) {
  return Buffer.from(value).toString('hex');
}

export function canonicalStringify(value) {
  if (value === null || typeof value !== 'object' || Array.isArray(value)) {
    if (Array.isArray(value)) return `[${value.map(canonicalStringify).join(',')}]`;
    return JSON.stringify(value);
  }
  const entries = Object.keys(value)
    .sort()
    .map((key) => (value[key] === undefined ? null : `${JSON.stringify(key)}:${canonicalStringify(value[key])}`))
    .filter((entry) => entry !== null);
  return `{${entries.join(',')}}`;
}

export function frame(payload) {
  const result = Buffer.alloc(payload.length + 4);
  result.writeUInt32BE(payload.length, 0);
  payload.copy(result, 4);
  return result;
}

export function parseIPv6(value) {
  const parts = value.replace(/^\[|\]$/g, '').split('::');
  const left = parts[0] ? parts[0].split(':') : [];
  const right = parts.length === 2 && parts[1] ? parts[1].split(':') : [];
  const groups = parts.length === 2
    ? [...left, ...Array(8 - left.length - right.length).fill('0'), ...right]
    : left;
  if (groups.length !== 8) throw new Error(`Invalid IPv6 address: ${value}`);
  const buffer = Buffer.alloc(16);
  groups.forEach((group, index) => buffer.writeUInt16BE(Number.parseInt(group, 16), index * 2));
  return buffer;
}

export function socksConnect(host, socksPort, targetHost, targetPort, timeout) {
  return new Promise((resolve, reject) => {
    const connection = net.createConnection({ host, port: socksPort });
    let buffer = Buffer.alloc(0);
    let stage = 'greeting';
    const fail = (cause) => {
      connection.destroy();
      reject(cause);
    };
    connection.setTimeout(timeout, () => fail(new Error('SOCKS5 timeout')));
    connection.once('error', fail);
    connection.once('connect', () => connection.write(Buffer.from([5, 1, 0])));
    connection.on('data', (chunk) => {
      buffer = Buffer.concat([buffer, chunk]);
      if (stage === 'greeting') {
        if (buffer.length < 2) return;
        if (buffer[0] !== 5 || buffer[1] !== 0) return fail(new Error(`SOCKS5 auth rejected: ${buffer[1]}`));
        buffer = buffer.subarray(2);
        stage = 'connect';
        const request = Buffer.alloc(22);
        request[0] = 5;
        request[1] = 1;
        request[2] = 0;
        request[3] = 4;
        parseIPv6(targetHost).copy(request, 4);
        request.writeUInt16BE(targetPort, 20);
        connection.write(request);
        return;
      }
      if (buffer.length < 4) return;
      const addressLength = buffer[3] === 4 ? 16 : buffer[3] === 1 ? 4 : buffer[4] + 1;
      if (buffer.length < 4 + addressLength + 2) return;
      if (buffer[1] !== 0) return fail(new Error(`SOCKS5 connect failed: ${buffer[1]}`));
      connection.removeAllListeners('data');
      connection.setTimeout(0);
      resolve(connection);
    });
  });
}

export function writeAndClose(connection, payload) {
  return new Promise((resolve, reject) => {
    connection.once('error', reject);
    connection.write(payload, (cause) => {
      if (cause) return reject(cause);
      connection.end();
      resolve();
    });
  });
}
