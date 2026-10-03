const fs = require('fs');
const path = require('path');
const { initializeTestEnvironment, assertFails, assertSucceeds } = require('@firebase/rules-unit-testing');
const { doc, setDoc, getDoc, updateDoc, deleteDoc, writeBatch, serverTimestamp, Timestamp } = require('firebase/firestore');

const PARENT = 'parentUid1', CHILD = 'childUid1', OTHER_PARENT = 'parentUid2', STRANGER = 'strangerUid';
const TOKEN = 'a'.repeat(32);
let env;

const parentCtx = (uid = PARENT) => env.authenticatedContext(uid, { firebase: { sign_in_provider: 'google.com' } }).firestore();
const childCtx = (uid = CHILD) => env.authenticatedContext(uid, { firebase: { sign_in_provider: 'anonymous' } }).firestore();
const inMinutes = (m) => Timestamp.fromMillis(Date.now() + m * 60000);

async function openPairing(token = TOKEN, expires = inMinutes(10)) {
  await assertSucceeds(setDoc(doc(parentCtx(), 'pairings', token),
    { parentUid: PARENT, status: 'open', expiresAt: expires, createdAt: serverTimestamp() }));
}
function claimBatch(db, cid = CHILD, token = TOKEN, pid = PARENT) {
  const b = writeBatch(db);
  b.update(doc(db, 'pairings', token), { status: 'claimed', claimedBy: cid });
  b.set(doc(db, 'families', pid, 'devices', cid),
    { deviceName: 'Tab', platform: 'Android', appVersion: '1.0', pairedAt: serverTimestamp(), pairingToken: token });
  return b.commit();
}
async function pairedSetup() {
  await assertSucceeds(setDoc(doc(parentCtx(), 'families', PARENT), { createdAt: serverTimestamp() }));
  await openPairing();
  await assertSucceeds(claimBatch(childCtx()));
}
const policyV = (v) => ({ version: v, updatedAt: serverTimestamp(), apps: {}, schedules: {}, controls: { lockdown: false } });

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'parental-rules-test',
    firestore: { rules: fs.readFileSync(path.join(__dirname, '..', 'firestore.rules'), 'utf8') }
  });
});
after(() => env.cleanup());
beforeEach(() => env.clearFirestore());

describe('pairing', () => {
  it('parent can create an open pairing; anonymous user cannot', async () => {
    await openPairing();
    await assertFails(setDoc(doc(childCtx(), 'pairings', 'b'.repeat(32)),
      { parentUid: CHILD, status: 'open', expiresAt: inMinutes(5), createdAt: serverTimestamp() }));
  });
  it('short token ids are rejected', async () => {
    await assertFails(setDoc(doc(parentCtx(), 'pairings', '123456'),
      { parentUid: PARENT, status: 'open', expiresAt: inMinutes(5), createdAt: serverTimestamp() }));
  });
  it('expiry longer than 30 minutes is rejected', async () => {
    await assertFails(setDoc(doc(parentCtx(), 'pairings', TOKEN),
      { parentUid: PARENT, status: 'open', expiresAt: inMinutes(120), createdAt: serverTimestamp() }));
  });
  it('pairings cannot be listed', async () => {
    await openPairing();
    const { collection, getDocs } = require('firebase/firestore');
    await assertFails(getDocs(collection(childCtx(), 'pairings')));
  });
  it('child can claim an open token and join the family', async () => {
    await pairedSetup();
    await assertSucceeds(getDoc(doc(childCtx(), 'families', PARENT, 'devices', CHILD)));
  });
  it('a token cannot be claimed twice', async () => {
    await pairedSetup();
    await assertFails(claimBatch(childCtx('childUid2')));
  });
  it('an expired token cannot be claimed', async () => {
    await env.withSecurityRulesDisabled(async (c) => {
      await setDoc(doc(c.firestore(), 'pairings', TOKEN),
        { parentUid: PARENT, status: 'open', expiresAt: Timestamp.fromMillis(Date.now() - 1000), createdAt: Timestamp.now() });
    });
    await assertFails(claimBatch(childCtx()));
  });
  it('child cannot create a device doc without claiming the token', async () => {
    await openPairing();
    await assertFails(setDoc(doc(childCtx(), 'families', PARENT, 'devices', CHILD),
      { deviceName: 'x', platform: 'Android', appVersion: '1', pairedAt: serverTimestamp(), pairingToken: TOKEN }));
  });
  it('an unlinked child cannot rejoin with its already-claimed token', async () => {
    await pairedSetup();
    await assertSucceeds(deleteDoc(doc(parentCtx(), 'families', PARENT, 'devices', CHILD)));
    await assertFails(setDoc(doc(childCtx(), 'families', PARENT, 'devices', CHILD),
      { deviceName: 'Tab', platform: 'Android', appVersion: '1.0', pairedAt: serverTimestamp(), pairingToken: TOKEN }));
  });
  it('child cannot join a different parent than the token names', async () => {
    await openPairing();
    await assertFails(claimBatch(childCtx(), CHILD, TOKEN, OTHER_PARENT));
  });
});

describe('policy', () => {
  beforeEach(pairedSetup);
  it('parent creates v1 then only v+1', async () => {
    const ref = (db) => doc(db, 'families', PARENT, 'devices', CHILD, 'policy', 'current');
    await assertSucceeds(setDoc(ref(parentCtx()), policyV(1)));
    await assertFails(updateDoc(ref(parentCtx()), policyV(1)));   // same version
    await assertFails(updateDoc(ref(parentCtx()), policyV(5)));   // skipped version
    await assertSucceeds(updateDoc(ref(parentCtx()), policyV(2)));
  });
  it('policy without controls or with unknown fields is rejected', async () => {
    const ref = (db) => doc(db, 'families', PARENT, 'devices', CHILD, 'policy', 'current');
    const noControls = { version: 1, updatedAt: serverTimestamp(), apps: {}, schedules: {} };
    await assertFails(setDoc(ref(parentCtx()), noControls));
    await assertFails(setDoc(ref(parentCtx()), { ...policyV(1), extra: 'x' }));
    await assertSucceeds(setDoc(ref(parentCtx()), policyV(1)));
  });
  it('child can read but never write policy', async () => {
    const ref = (db) => doc(db, 'families', PARENT, 'devices', CHILD, 'policy', 'current');
    await assertSucceeds(setDoc(ref(parentCtx()), policyV(1)));
    await assertSucceeds(getDoc(ref(childCtx())));
    await assertFails(updateDoc(ref(childCtx()), policyV(2)));
  });
  it('another parent and a stranger cannot read or write', async () => {
    const ref = (db) => doc(db, 'families', PARENT, 'devices', CHILD, 'policy', 'current');
    await assertSucceeds(setDoc(ref(parentCtx()), policyV(1)));
    await assertFails(getDoc(ref(parentCtx(OTHER_PARENT))));
    await assertFails(getDoc(ref(childCtx(STRANGER))));
    await assertFails(setDoc(ref(parentCtx(OTHER_PARENT)), policyV(2)));
  });
});

describe('status, usage, audit, unlink', () => {
  beforeEach(pairedSetup);
  const base = (...p) => ['families', PARENT, 'devices', CHILD, ...p];
  it('child writes heartbeat with server time only; parent reads; parent cannot write it', async () => {
    const hb = { lastSeen: serverTimestamp(), ackedPolicyVersion: 1, isDeviceOwner: false, accessibilityEnabled: true, appVersion: '1.0' };
    await assertSucceeds(setDoc(doc(childCtx(), ...base('status', 'heartbeat')), hb));
    await assertFails(setDoc(doc(childCtx(), ...base('status', 'heartbeat')), { ...hb, lastSeen: Timestamp.fromMillis(1) }));
    await assertSucceeds(getDoc(doc(parentCtx(), ...base('status', 'heartbeat'))));
    await assertFails(setDoc(doc(parentCtx(), ...base('status', 'heartbeat')), hb));
  });
  it('audit is append-only', async () => {
    const ref = doc(childCtx(), ...base('audit', 'e1'));
    await assertSucceeds(setDoc(ref, { event: 'X', details: 'd', createdAt: serverTimestamp() }));
    await assertFails(updateDoc(ref, { details: 'tampered' }));
    await assertFails(deleteDoc(ref));
  });
  it('child writes inventory, parent reads, parent cannot write it', async () => {
    const inv = { apps: [{ pkg: 'com.a', name: 'A' }], updatedAt: serverTimestamp() };
    await assertSucceeds(setDoc(doc(childCtx(), ...base('inventory', 'current')), inv));
    await assertSucceeds(getDoc(doc(parentCtx(), ...base('inventory', 'current'))));
    await assertFails(setDoc(doc(parentCtx(), ...base('inventory', 'current')), inv));
    await assertFails(setDoc(doc(childCtx(), ...base('inventory', 'other')), inv));
  });
  it('usage day key must be a date', async () => {
    await assertSucceeds(setDoc(doc(childCtx(), ...base('usage', '2026-10-03')), { minutesByPackage: {}, updatedAt: serverTimestamp() }));
    await assertFails(setDoc(doc(childCtx(), ...base('usage', 'whenever')), { minutesByPackage: {}, updatedAt: serverTimestamp() }));
  });
  it('parent can unlink; afterwards the child loses access', async () => {
    await assertSucceeds(getDoc(doc(childCtx(), ...base())));
    await assertSucceeds(deleteDoc(doc(parentCtx(), ...base())));
    await assertFails(getDoc(doc(childCtx(), ...base('policy', 'current'))));
  });
  it('child cannot delete its own device doc (needs parent or PIN-gated flow)', async () => {
    await assertFails(deleteDoc(doc(childCtx(), ...base())));
  });
});
