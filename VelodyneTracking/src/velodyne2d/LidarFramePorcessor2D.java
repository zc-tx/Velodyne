package velodyne2d;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import prepocess.ConnCompFilter;
import calibration.BodyFrame;
import calibration.CoordinateFrame;
import detection.LineExtractor;
import detection.Motion;
import detection.VehicleInitializer;
import detection.VehicleModel;
import VelodyneDataIO.LidarFrame;
import VelodyneView.LidarFrameProcessor;

public class LidarFramePorcessor2D {

	private LidarFrameProcessor processor3D;
	private VirtualScanFactory scanFac;
	private VehicleModel egoVehicle;
	private ConnCompFilter compFilter;
	private ConnCompFilter smallCompFilter;
	private FrameTransformer2D trans;
	
	public BodyFrame bodyFrame;
	public double timestamp;
	
	private VehicleInitializer vehInitializer;
	private RangeFilter2D rangeFilter;
	
	public LidarFramePorcessor2D(LidarFrameProcessor lp) {
		processor3D = lp;
		scanFac = new VirtualScanFactory();
		trans = new FrameTransformer2D();
		vehInitializer = new VehicleInitializer();
	}
	
	public void readNextFrame() throws Exception{
		this.processor3D.readNextFrame();
		LidarFrame lf = processor3D.getCurFrame();
		bodyFrame = lf.getBodyFrame();
		timestamp = lf.getTime();
		//connComp 0.5, 10 for segment generation 
		if(compFilter == null) compFilter = new ConnCompFilter(processor3D.getVirtualTable());
		compFilter.findConnComp(processor3D.getVirtualTable(), null, 0.5, 10);
		//connComp 0.3, 5 for noise filtering
		if(smallCompFilter == null) smallCompFilter = new ConnCompFilter(processor3D.getVirtualTable());
		smallCompFilter.findConnComp(processor3D.getVirtualTable(), null, 0.3, 5);
		//make scan and segments
		scanFac.convert3Dto2D(this.processor3D.getVirtualTable(), lf.getLocalWorldFrame(), lf.getBodyFrame(), smallCompFilter, compFilter);
		//make ego vehicle
		BodyFrame egoFrame = lf.getBodyFrame();		
		this.egoVehicle = new VehicleModel(egoFrame.getPosion2D(), egoFrame.getBodyY2D(), VehicleModel.default_width, VehicleModel.default_length, 0);
	}
	//filter based on the ego bodyFrame
	public void rangeFilter(double xmin, double xmax, double ymin, double ymax){
		if(rangeFilter==null){
			rangeFilter = new RangeFilter2D(scanFac.getScan().getColNum());
		}
		rangeFilter.makeRangeMask(scanFac.getScan(), trans, xmin, xmax, ymin, ymax);
	}
	
	public boolean[] getRangeMask(){
		if(rangeFilter==null){
			return null;
		}else{
			return rangeFilter.getMask();
		}
	}
	/**
	 * motion detection
	 * @return
	 */
	public List<Segment> findMotionSegment(){
		List<Segment> segments = new ArrayList<Segment>();
		//if not ready, skip
		if(!this.scanFac.hasAllFrames()) return segments;
		//detect motion using Motion object and binded with segment object
		for(Segment seg: this.scanFac.getSegments()){
			Motion motion = new Motion(seg); 
			motion.detectMotionFromPrev(getPrevScan());
			motion.detectMotionFromNext(getNextScan());
//			if(seg.getMotion().isMoving()!=0) 
			segments.add(seg);
		}
		return segments;
	}
	/**
	 * line extraction
	 * @param segments
	 * @return
	 */
	public List<Segment> extractLines(List<Segment> segments){
		List<Segment> newSegments = new ArrayList<Segment>();
		for(Segment seg: segments){
			LineExtractor lineExt = new LineExtractor(seg);
			if(lineExt.extractLines()){
				newSegments.add(seg);
			}
		}
		return newSegments;
	}
	
	
	public VehicleInitializer getVehInitializer(){
		return vehInitializer;
	}
	
	public Point2D[] transformSegmentsInPrevScan(Segment seg){
		VirtualScan prevScan = this.scanFac.getPrevScan();
		if(prevScan == null) return new Point2D[0];
		return this.trans.transform(scanFac.getScan().getLocalWorldFrame(), prevScan.getLocalWorldFrame(), seg.getPoints());
	}
	
	public VirtualScan getPrevScan(){
		return this.scanFac.getPrevScan();
	}
	
	public VirtualScan getNextScan(){
		return this.scanFac.getNextScan();
	}
	
	public VirtualScan getScan(){
		return this.scanFac.getScan();
	}
	
	public Collection<Segment> getSegments(){
		return this.scanFac.getSegments();
	}
	
	public VehicleModel getEgoVehicle(){
		return egoVehicle;
	}
	
	public LidarFrameProcessor get3DProcessor(){
		return processor3D;
	}
	
	public void stop(){
		this.processor3D.stop();
	}
	
	public boolean isReady(){
		return this.scanFac.hasAllFrames();
	}
}
