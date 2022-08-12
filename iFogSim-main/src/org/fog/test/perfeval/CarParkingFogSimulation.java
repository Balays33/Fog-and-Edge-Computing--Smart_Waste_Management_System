package org.fog.test.perfeval;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.sdn.overbooking.BwProvisionerOverbooking;
import org.cloudbus.cloudsim.sdn.overbooking.PeProvisionerOverbooking;
import org.fog.application.AppEdge;
import org.fog.application.AppLoop;
import org.fog.application.Application;
import org.fog.application.selectivity.FractionalSelectivity;
import org.fog.entities.Actuator;
import org.fog.entities.ArchType;
import org.fog.entities.FogBroker;
import org.fog.entities.FogDevice;
import org.fog.entities.FogDeviceCharacteristics;
import org.fog.entities.OsType;
import org.fog.entities.Sensor;
import org.fog.entities.Tuple;
import org.fog.entities.VmmType;
import org.fog.placement.Controller;
import org.fog.placement.ModuleMapping;
import org.fog.placement.ModulePlacement;
import org.fog.placement.ModulePlacementEdgewards;
import org.fog.placement.ModulePlacementMapping;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.entities.SensorType;
import org.fog.utils.FogLinearPowerModel;
import org.fog.utils.FogUtils;
import org.fog.utils.TimeKeeper;
import org.fog.utils.distribution.DeterministicDistribution;

/**
 * Simulation setup for the FEC-based car parking system
 * @author Apostolos Giannakidis
 * @
 */
public class CarParkingFogSimulation {
	private static final List<FogDevice> FOG_DEVICES = new ArrayList<FogDevice>();
	private static final List<Sensor> SENSORS = new ArrayList<Sensor>();
	private static final List<Actuator> ACTUATORS = new ArrayList<Actuator>();
	private static final Map<ConfigName, Configuration> CONFIGS = new HashMap<ConfigName, Configuration>();

	// The Configuration to use for the simulation
	// Change the config name in order to run a simulation with different configuration
	private static final ConfigName CONFIG_NAME = ConfigName.CONFIG_1;
	
	private static final int NUMBER_OF_AREAS;
	private static final int SENSORS_PER_AREA;
	private static final int CAMERAS_PER_AREA;
	private static final boolean CLOUD_BASED;
	
	private static class Configuration {
		int areasCount;
		int sensorsPerArea;
		int camerasPerArea;
		boolean cloudBased;
		
		Configuration(int areasCount,
				int sensorsPerArea, int camerasPerArea, boolean cloudBased) {
			this.areasCount = areasCount;
			this.sensorsPerArea = sensorsPerArea;
			this.camerasPerArea = camerasPerArea;
			this.cloudBased = cloudBased;
		}
		
		@Override
		public String toString() {
			StringBuilder str = new StringBuilder();
			str.append("areasCount=").append(areasCount).append("\n");
			str.append("sensorsPerArea=").append(sensorsPerArea).append("\n");
			str.append("camerasPerArea=").append(camerasPerArea).append("\n");
			str.append("cloudBased=").append(cloudBased).append("\n");
			return str.toString();
		}
	}
	
	private enum ConfigName {
		CONFIG_1,
		CONFIG_2,
		CONFIG_3,
		CONFIG_4,
		CONFIG_5,
		CONFIG_6;
	}
	
	static {
		createConfigurations();
		
		Configuration config = CONFIGS.get(CONFIG_NAME);

		Log.printLine("Loading Simulation Configuration: " + CONFIG_NAME);
		
		// now load the selected configuration
		NUMBER_OF_AREAS = config.areasCount;
		SENSORS_PER_AREA = config.sensorsPerArea;
		CAMERAS_PER_AREA = config.camerasPerArea;
		CLOUD_BASED = config.cloudBased;
		
		Log.printLine("Loaded Simulation Configuration:");
		Log.printLine(config);
	}
	
	private static void createConfigurations() {
		
		/* Config 1
		   Areas: 5
		   Edge nodes: 5
		   Sensors per area: 5
		   Cameras per area: 2
		*/
		CONFIGS.put(ConfigName.CONFIG_1,
				new Configuration(5, 5, 2, false) {});
		
		/* Config 2 - Same as Config 1 but all sensors/cameras are connected directly to the Cloud
		   via the local Internet gateway.
		   Areas: 5
		   Edge nodes: 0
		   Sensors per area: 5
		   Cameras per area: 2
		 */
		CONFIGS.put(ConfigName.CONFIG_2,
				new Configuration(5, 5, 2, true) {});

		/* Config 3
		   Areas: 10
		   Edge nodes: 10
		   Sensors per area: 5
		   Cameras per area: 2
		 */
		CONFIGS.put(ConfigName.CONFIG_3,
				new Configuration(10, 5, 2, false) {});
		
		/* Config 4 - Same as Config 3 but all sensors/cameras are connected directly to the Cloud
		   via the local Internet gateway.
		   Areas: 10
		   Edge nodes: 0
		   Sensors per area: 5
		   Cameras per area: 2
		 */
		CONFIGS.put(ConfigName.CONFIG_4,
				new Configuration(10, 5, 2, true) {});

		/* Config 5 - Increased number of sensors/cameras in each area.
		   Areas: 10
		   Edge nodes: 10
		   Sensors per area: 10
		   Cameras per area: 4
		 */
		CONFIGS.put(ConfigName.CONFIG_5,
				new Configuration(10, 8, 4, false) {});
		
		/* Config 6 - Same as Config 5 but all sensors/cameras are connected directly to the Cloud
		   via the local Internet gateway.
		   Areas: 10
		   Edge nodes: 0
		   Sensors per area: 10
		   Cameras per area: 4
		 */
		CONFIGS.put(ConfigName.CONFIG_6,
				new Configuration(10, 8, 4, true) {});
	}
	
	public static void main(String[] args) {
		
		Log.printLine("Starting Car Parking FEC Simulation...");

		try {
			Log.disable();
			int num_user = 1; // number of cloud users
			Calendar calendar = Calendar.getInstance();
			boolean trace_flag = true; // mean trace events

			CloudSim.init(num_user, calendar, trace_flag);

			String appId = "car-parking"; // identifier of the application
			
			FogBroker broker = new FogBroker("broker");
			
			Application application = createApplication(appId, broker.getId());
			application.setUserId(broker.getId());
			
			FogDevice cloud = createCloudEnvironment();
			FogDevice gateway = createLocalGateway(cloud.getId());
			
			createAreas(broker.getId(), appId,  gateway.getId());
			
			ModuleMapping moduleMapping = ModuleMapping.createModuleMapping(); // initializing a module mapping
			for(FogDevice device : FOG_DEVICES){
				String deviceName = device.getName();
				if(deviceName.startsWith("camera")){
					moduleMapping.addModuleToDevice("motion_detector", deviceName);  // fixing 1 instance of the Motion Detector module to each Smart Camera
				}
				if(deviceName.startsWith("ir-sensor")){
					moduleMapping.addModuleToDevice("ir_detector", deviceName);  // fixing 1 instance of the Motion Detector module to each Smart Camera
				}
			}
			
			moduleMapping.addModuleToDevice("user_interface", "cloud"); // fixing instances of User Interface module in the Cloud
			if(CLOUD_BASED){
				// if the mode of deployment is cloud-based
				moduleMapping.addModuleToDevice("object_detector", "cloud"); // placing all instances of Object Detector module in the Cloud
				moduleMapping.addModuleToDevice("object_tracker", "cloud"); // placing all instances of Object Tracker module in the Cloud
				moduleMapping.addModuleToDevice("ir_detector", "cloud");
				moduleMapping.addModuleToDevice("parking_space_detector", "cloud");
			}

			Controller controller = new Controller("master-controller", FOG_DEVICES, SENSORS, ACTUATORS);
			
			ModulePlacement modulePlacement = null;
			
			if (CLOUD_BASED) {
				modulePlacement = new ModulePlacementMapping(FOG_DEVICES, application, moduleMapping);
			} else {
				modulePlacement = new ModulePlacementEdgewards(FOG_DEVICES, SENSORS, ACTUATORS, application, moduleMapping);
			}

			controller.submitApplication(application, modulePlacement);


			TimeKeeper.getInstance().setSimulationStartTime(Calendar.getInstance().getTimeInMillis());
			
			CloudSim.startSimulation();

			CloudSim.stopSimulation();

			Log.printLine("Car Parking FEC Simulation finished!");
		} catch (Exception e) {
			e.printStackTrace();
			Log.printLine("Unwanted errors happen");
		}
	}
	
	private static FogDevice createCloudEnvironment() {
		int level = 0;
		long mips = 50000;
		double ratePerMips = 0.02d;
		FogDevice cloud = createFogDevice("cloud", mips, 40000, 100, 10000, level,
				ratePerMips, 16*103, 16*83.25);
		cloud.setParentId(-1);
		FOG_DEVICES.add(cloud);
		return cloud;
	}
	
	private static FogDevice createLocalGateway(int parentId) {
		int level = 1;
		long mips = 3000;
		double ratePerMips = 0.05;
		FogDevice gateway = createFogDevice("gateway-server", mips, 4000, 10000, 10000, level,
				ratePerMips, 107.339, 83.4333);
		gateway.setTotalCost(10000);
		gateway.setParentId(parentId);
		gateway.setUplinkLatency(120); // latency of connection between GW and Cloud is 120 ms
		FOG_DEVICES.add(gateway);
		return gateway;
	}

	private static FogDevice createEdgeNode(String id, int parentId) {
		int level = 2;
		long mips = 2000;
		double ratePerMips = 0.05;
		FogDevice edgeNode = createFogDevice(id, mips, 4000, 10000, 10000, level,
				ratePerMips, 107.339, 83.4333);
		edgeNode.setUplinkLatency(2); // latency of connection between edge node and gateway is 2 ms
		edgeNode.setParentId(parentId);
		FOG_DEVICES.add(edgeNode);
		return edgeNode;
	}
	
	private static void createAreas(int userId, String appId, int parentId) {
		for(int i=0; i < NUMBER_OF_AREAS; i++) {
			String areaName = String.format("area#%s", i);
			addArea(areaName, userId, appId, parentId);
		}
	}
	
	private static void addArea(String id, int userId, String appId, int parentId){
		FogDevice router = createEdgeNode("EdgeNode-"+id, parentId);
		
		for(int i=0; i< SENSORS_PER_AREA; i++){
			String mobileId = id+"-"+i;
			FogDevice irSensor = addIRSensor(mobileId, userId, appId, router.getId()); // adding a smart camera to the physical topology. Smart cameras have been modeled as fog devices as well.
			irSensor.setUplinkLatency(2); // latency of connection between IR-Sensor and edge node is 2 ms
			FOG_DEVICES.add(irSensor);
		}
		
		for(int i=0;i<CAMERAS_PER_AREA;i++){
			String mobileId = id+"-"+i;
			FogDevice camera = addCamera(mobileId, userId, appId, router.getId()); // adding a smart camera to the physical topology. Smart cameras have been modeled as fog devices as well.
			camera.setUplinkLatency(2); // latency of connection between camera and router is 2 ms
			FOG_DEVICES.add(camera);
		}
	}
	
	private static FogDevice addIRSensor(String id, int userId, String appId, int parentId){	
		int level = 3;
		long mips = 200;
		double ratePerMips = 0.001;

		FogDevice irSensor = createFogDevice("ir-sensor-"+id, mips, 1000, 10000, 10000, level,
				ratePerMips, 87.53, 82.44);
		irSensor.setParentId(parentId);
		Sensor sensor = new Sensor("s-"+id, SensorType.IR_SENSOR.toString(), userId, appId, new DeterministicDistribution(5)); // inter-transmission time of camera (sensor) follows a deterministic distribution
		SENSORS.add(sensor);
		Actuator ptz = new Actuator("ptz-"+id, userId, appId, "PTZ_CONTROL");
		ACTUATORS.add(ptz);
		sensor.setGatewayDeviceId(irSensor.getId());
		sensor.setLatency(1.0);  // latency of connection between IR Sensor and the parent Smart Camera is 1 ms;
		ptz.setGatewayDeviceId(irSensor.getId());
		ptz.setLatency(1.0);  // latency of connection between PTZ Control and the parent Smart Camera is 1 ms
		return irSensor;
	}
	
	private static FogDevice addCamera(String id, int userId, String appId, int parentId){
		int level = 3;
		long mips = 500;
		double ratePerMips = 1d;
		FogDevice camera = createFogDevice("camera-"+id, mips, 1000, 10000, 10000, level,
				ratePerMips, 87.53, 82.44);
		camera.setParentId(parentId);
		Sensor sensor = new Sensor("cs-"+id, SensorType.CAMERA.toString(), userId, appId, new DeterministicDistribution(5)); // inter-transmission time of camera (sensor) follows a deterministic distribution
		SENSORS.add(sensor);
		Actuator ptz = new Actuator("cptz-"+id, userId, appId, "PTZ_CONTROL");
		ACTUATORS.add(ptz);
		sensor.setGatewayDeviceId(camera.getId());
		sensor.setLatency(1.0);  // latency of connection between camera (sensor) and the parent Smart Camera is 1 ms
		ptz.setGatewayDeviceId(camera.getId());
		ptz.setLatency(1.0);  // latency of connection between PTZ Control and the parent Smart Camera is 1 ms
		return camera;
	}
	
	/**
	 * Creates a vanilla fog device
	 * @param nodeName name of the device to be used in simulation
	 * @param mips MIPS
	 * @param ram RAM
	 * @param upBw uplink bandwidth
	 * @param downBw downlink bandwidth
	 * @param level hierarchy level of the device
	 * @param ratePerMips cost rate per MIPS used
	 * @param busyPower
	 * @param idlePower
	 * @return
	 */
	private static FogDevice createFogDevice(String nodeName, long mips,
			int ram, long upBw, long downBw, int level,
			double ratePerMips, double busyPower, double idlePower) {
		
		List<Pe> peList = new ArrayList<Pe>();

		// 3. Create PEs and add these into a list.
		peList.add(new Pe(0, new PeProvisionerOverbooking(mips))); // need to store Pe id and MIPS Rating

		int hostId = FogUtils.generateEntityId();
		long storage = 1000000; // host storage

		PowerHost host = new PowerHost(
				hostId,
				new RamProvisionerSimple(ram),
				new BwProvisionerOverbooking(Long.MAX_VALUE),
				storage,
				peList,
				new StreamOperatorScheduler(peList),
				new FogLinearPowerModel(busyPower, idlePower)
			);

		List<Host> hostList = new ArrayList<Host>();
		hostList.add(host);

		double time_zone = 10.0; // time zone this resource located
		double cost = 3.0; // the cost of using processing in this resource
		double costPerMem = 0.05; // the cost of using memory in this resource
		double costPerStorage = 0.001; // the cost of using storage in this
										// resource
		double costPerBw = 2.0; // the cost of using bw in this resource
		LinkedList<Storage> storageList = new LinkedList<Storage>(); // we are not adding SAN
													// devices by now

		FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(
				ArchType.x86, OsType.Linux, VmmType.Xen,
				host, time_zone, cost, costPerMem,
				costPerStorage, costPerBw);

		FogDevice fogdevice = null;
		try {
			fogdevice = new FogDevice(nodeName, characteristics, 
					new AppModuleAllocationPolicy(hostList),
					storageList, 10, upBw, downBw, 0, ratePerMips);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		fogdevice.setLevel(level);
		return fogdevice;
	}

	/**
	 * Function to create the Intelligent Surveillance application in the DDF model. 
	 * @param appId unique identifier of the application
	 * @param userId identifier of the user of the application
	 * @return
	 */
	@SuppressWarnings({"serial" })
private static Application createApplication(String appId, int userId){
		
		Application application = Application.createApplication(appId, userId);
		/*
		 * Adding modules (vertices) to the application model (directed graph)
		 */
		application.addAppModule("object_detector", 10);
		application.addAppModule("motion_detector", 10);
		application.addAppModule("object_tracker", 10);
		application.addAppModule("user_interface", 10);
		
		application.addAppModule("ir_detector", 20);
		application.addAppModule("parking_space_detector", 20);
		application.addAppModule("parking_space_tracker", 20);
		
		/*
		 * Connecting the application modules (vertices) in the application model (directed graph) with edges
		 */
		application.addAppEdge(SensorType.IR_SENSOR.toString(), "ir_detector", 1000, 20000, SensorType.IR_SENSOR.toString(), Tuple.UP, AppEdge.SENSOR); // adding edge from CAMERA (sensor) to Motion Detector module carrying tuples of type CAMERA
		application.addAppEdge("CAMERA", "motion_detector", 1000, 20000, "CAMERA", Tuple.UP, AppEdge.SENSOR); // adding edge from CAMERA (sensor) to Motion Detector module carrying tuples of type CAMERA
		application.addAppEdge("ir_detector", "parking_space_detector", 2000, 2000, "IR_STREAM", Tuple.UP, AppEdge.MODULE); // adding edge from Motion Detector to Object Detector module carrying tuples of type MOTION_VIDEO_STREAM
		application.addAppEdge("motion_detector", "object_detector", 2000, 2000, "MOTION_VIDEO_STREAM", Tuple.UP, AppEdge.MODULE); // adding edge from Motion Detector to Object Detector module carrying tuples of type MOTION_VIDEO_STREAM
		application.addAppEdge("object_detector", "user_interface", 500, 2000, "DETECTED_OBJECT", Tuple.UP, AppEdge.MODULE); // adding edge from Object Detector to User Interface module carrying tuples of type DETECTED_OBJECT
		application.addAppEdge("parking_space_detector", "user_interface", 500, 2000, "DETECTED_OBJECT", Tuple.UP, AppEdge.MODULE); // adding edge from Object Detector to User Interface module carrying tuples of type DETECTED_OBJECT
		application.addAppEdge("object_detector", "object_tracker", 1000, 100, "OBJECT_LOCATION", Tuple.UP, AppEdge.MODULE); // adding edge from Object Detector to Object Tracker module carrying tuples of type OBJECT_LOCATION
		application.addAppEdge("object_tracker", "PTZ_CONTROL", 100, 28, 100, "PTZ_PARAMS", Tuple.DOWN, AppEdge.ACTUATOR); // adding edge from Object Tracker to PTZ CONTROL (actuator) carrying tuples of type PTZ_PARAMS
		
		/*
		 * Defining the input-output relationships (represented by selectivity) of the application modules. 
		 */
		application.addTupleMapping("ir_detector", SensorType.IR_SENSOR.toString(), "IR_STREAM", new FractionalSelectivity(1.0)); // 1.0 tuples of type MOTION_VIDEO_STREAM are emitted by Motion Detector module per incoming tuple of type CAMERA
		application.addTupleMapping("motion_detector", "CAMERA", "MOTION_VIDEO_STREAM", new FractionalSelectivity(1.0)); // 1.0 tuples of type MOTION_VIDEO_STREAM are emitted by Motion Detector module per incoming tuple of type CAMERA
		application.addTupleMapping("object_detector", "MOTION_VIDEO_STREAM", "OBJECT_LOCATION", new FractionalSelectivity(1.0)); // 1.0 tuples of type OBJECT_LOCATION are emitted by Object Detector module per incoming tuple of type MOTION_VIDEO_STREAM
		application.addTupleMapping("object_detector", "MOTION_VIDEO_STREAM", "DETECTED_OBJECT", new FractionalSelectivity(0.05)); // 0.05 tuples of type MOTION_VIDEO_STREAM are emitted by Object Detector module per incoming tuple of type MOTION_VIDEO_STREAM
		application.addTupleMapping("parking_space_detector", SensorType.IR_SENSOR.toString(), "IR_STREAM", new FractionalSelectivity(1.0)); // 1.0 tuples of type MOTION_VIDEO_STREAM are emitted by Motion Detector module per incoming tuple of type CAMERA

		/*
		 * Defining application loops (maybe incomplete loops) to monitor the latency of. 
		 */
		final AppLoop loop1 = new AppLoop(new ArrayList<String>() {
			{
				add("motion_detector");
				add("object_detector");
				add("object_tracker");
			}
		});
		final AppLoop loop2 = new AppLoop(new ArrayList<String>() {
			{
				add("ir_detector");
				add("parking_space_detector");
			}
		});
		List<AppLoop> loops = new ArrayList<AppLoop>() {
			{
				add(loop1);
				add(loop2);
			}
		};
		
		application.setLoops(loops);
		return application;
	}
}