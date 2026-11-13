import { NativeModules, Platform } from 'react-native';

type NativeTripBridgeType = {
  startLocationService: (tripId:string)=>Promise<void>;
  stopLocationService: ()=>Promise<void>;
  getBluetoothDevices: ()=>Promise<Array<{name:string;address:string;type:'ble'|'classic'}>>;
  connectBluetooth: (address:string)=>Promise<boolean>;
  disconnectBluetooth: ()=>Promise<void>;
  readObdPid: (pid:string)=>Promise<number|null>;
};

const bridge = NativeModules.NativeTripBridge as NativeTripBridgeType | undefined;

/** Native functions are deliberately guarded until Android Kotlin modules are implemented. */
export const NativeTripBridge = {
  available: Platform.OS === 'android' && !!bridge,
  async startLocationService(tripId:string) {
    if (!bridge) throw new Error('Android location native module is not installed yet');
    return bridge.startLocationService(tripId);
  },
  async stopLocationService() {
    if (!bridge) throw new Error('Android location native module is not installed yet');
    return bridge.stopLocationService();
  },
  async getBluetoothDevices() {
    if (!bridge) throw new Error('Android Bluetooth native module is not installed yet');
    return bridge.getBluetoothDevices();
  },
  async connectBluetooth(address:string) {
    if (!bridge) throw new Error('Android Bluetooth native module is not installed yet');
    return bridge.connectBluetooth(address);
  },
  async disconnectBluetooth() {
    if (!bridge) throw new Error('Android Bluetooth native module is not installed yet');
    return bridge.disconnectBluetooth();
  },
  async readObdPid(pid:string) {
    if (!bridge) throw new Error('Android OBD native module is not installed yet');
    return bridge.readObdPid(pid);
  },
};
