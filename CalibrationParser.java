import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Scanner;
import ij.gui.NewImage;
import ij.io.FileSaver;
import ij.plugin.*;
import ij.plugin.frame.*;
import ij.gui.OvalRoi;
public class CalibrationParser{
	String _folderpath = "C:\\Users\\Micha\\Desktop\\Scientific\\Lepton Calibration\\DEC_16_lepton\\Calib_October_17\\";
	int _width = 160;
	int _height = 120;
	boolean _useJPEGs = false;
	boolean _serialise = false;
	boolean _print = true;
	String _rootPath;
	Canny_Edge_Detector _edgeDetector = new Canny_Edge_Detector();
	HashMap<String,double[]> _folderTemperatures = new HashMap<String,double[]>();

	public void run(String rootPath,double ratio) {
		//ROI oval represented by box surrounding it; x0,y0 are topleft corner.
		_rootPath = rootPath;
		loadTemperatures();
		ArrayList<String> subfolders = getSubFolderPaths(rootPath);
		
		writeToFile(rootPath+"RAW_.txt","#TIBB Temp\tSigma\tSignal Value\tSigma\tFPA Temperature\tradius\tFilename\n",false);
		writeToFile(rootPath+"RAW_.txt","#ratio="+ratio+"\n",true);
		writeToFile(rootPath+"AVERAGE_.txt","#Tibb Temp\tSigma\tFPAMean\tFPASigma\tSignalMean\tSignalSigma\n",false);
		writeToFile(rootPath+"AVERAGE_.txt","#ratio="+ratio+"\n",true);
		//	String str = tibbTemp[0] + "\t" + tibbTemp[1]+"\t"+FPAMean +"\t"+FPASigma+"\t"+signalMean+"\t"+signalSigma+"\n";

		for(String folderpath:subfolders) {
			if (_print) {
				System.out.println(folderpath + " loading");
			}
			parseFolder(folderpath,ratio);
		}

	}
	public void parseFolder(String folderpath,double ratio) {
		double signalMean = 0,signalSigma = 0;
		double FPAMean = 0, FPASigma = 0;
		double signal,FPATemp;
		int midX = _width/2;
		int midY = _height/2;

		ArrayList<String> files = getFilepathsInFolder(folderpath);
		ArrayList<Double> FPAValues = new ArrayList<Double>();
		ArrayList<Double> signalValues = new ArrayList<Double>();
		double[] tibbTemp = _folderTemperatures.get(getNameFromPath(folderpath));

		//writeToFile(_rootPath+"log.txt",folderpath + "\n",true);
		//writeToFile(_rootPath+"log.txt","File\tSignal Value\tFPA Temperature \t ROI Size\n",true);
		for(int i = 0; i<files.size(); i++) {
			String filepath = files.get(i);
			boolean flag = true;
			ImagePlus image = loadImagePlus(filepath);
			ttd(image);
			double roiRadius = ttd(image);
			roiRadius*=ratio;
			OvalRoi roi = createROI(roiRadius);
			image.setRoi(roi);
			ImageStatistics stats = image.getProcessor().getStatistics();
			signal = stats.mean;
			if (roiRadius>20 || roiRadius<4 || stats.stdDev>30) {
				flag = false;
				i+=3;
			}
			if (flag) {
				signalValues.add(signal);
				FPATemp = getFPATemp(filepath); 
				FPAValues.add(FPATemp);
				String str = tibbTemp[0] + "\t" + tibbTemp[1] + "\t" + signal + "\t" + stats.stdDev + "\t" + FPATemp +"\t" + roiRadius + "\t#" + getNameAndParentFolder(filepath) + "\n";
				writeToFile(_rootPath+"RAW_.txt",str,true);
			}
		}
		if (tibbTemp!=null) {
			signalMean = calculateMean(signalValues);
			signalSigma = calculateSigma(signalMean,signalValues);
			FPAMean = calculateMean(FPAValues);
			FPASigma = calculateSigma(FPAMean,FPAValues);
			String str = tibbTemp[0] + "\t" + tibbTemp[1]+"\t"+FPAMean +"\t"+FPASigma+"\t"+signalMean+"\t"+signalSigma+"\n";
			writeToFile(_rootPath+"AVERAGE_.txt",str,true);
		}
	}	
	public void delete(String filepath) {
		File file1 = new File(filepath);
		if (!file1.delete()) {
			System.out.println(filepath + " could not be deleted");
		}
		filepath = filepath.replace(".csv",".pgm");
		file1 = new File(filepath);
		if (file1.exists()) {
			if (!file1.delete()) {
				System.out.println(filepath + " could not be deleted");
			}
		}
	}
	public String getNameFromPath(String filepath) {
		String[] arr = filepath.split("\\\\");
		return arr[arr.length-1];
	}
	public String getNameAndParentFolder(String filepath) {
		String[] arr = filepath.split("\\\\");
		return arr[arr.length-2] + "\\" + arr[arr.length-1];
	}
	public void writeToFile(String filepath, String line, boolean append) {
		try {
			PrintWriter  out = new PrintWriter(new FileOutputStream(new File(filepath), append));
			out.write(line);
			out.close();
		}catch (IOException e) {
			System.out.println(e.getMessage());
		}
	}
	public void loadTemperatures() {
		String infoPath = _rootPath + "\\info.txt";
		try {
			BufferedReader br = new BufferedReader(new FileReader(infoPath));
			String line = br.readLine();
			while(line!=null) {
				Scanner s = new Scanner(line);
				s.useDelimiter("\t");
				double[] arr =new double[2];
				String foldername = s.next();
				arr[0] = s.nextDouble();
				arr[1] = s.nextDouble();
				_folderTemperatures.put(foldername,arr);
				line =br.readLine();
				s.close();
			}
			br.close();
		}catch(IOException e) {
			System.out.println(e.getMessage());
		}
	}
	public double calculateSigma(double mean, ArrayList<Double> values) {
		//return sample deviation
		double sd = 0;
		for(double x_i:values) {
			sd+=(x_i-mean)*(x_i-mean);
		}
		sd/=(values.size()-1);
		sd = Math.sqrt(sd);
		return sd;
	}
	public double calculateMean(ArrayList<Double> values) {
		double sum = 0;
		if (values.size()==0) {
			return 0;
		}
		for (double value:values) {
			sum+=value;
		}
		return sum/values.size();
	}
	public OvalRoi createROI(double radius) {
		int x1 = (int) (_width/2-radius);
		int y1 = (int) (_height/2-radius);
		OvalRoi roi = new OvalRoi(x1,y1,2*radius,2*radius);
		return roi;
	}
	public double ttd(ImagePlus img) {
		double radius = 7;
		double currentStdev = 0;
		double prevStdev = 0;
		OvalRoi roi = createROI(radius);
		img.setRoi(roi);
		currentStdev = img.getProcessor().getStatistics().stdDev;
		prevStdev = currentStdev;
		 do  {
				prevStdev = currentStdev;
				roi = createROI(radius);
				img.setRoi(roi);
				currentStdev = img.getProcessor().getStatistics().stdDev;
				radius++;
			}while (radius<30 && Math.abs(currentStdev-prevStdev)<3);
		return radius-1;
	}
	public int getROIRadiusFromEdgeDetectedImage(ImagePlus img) {
		int midX = _width/2;
		int midY = _height/2;
		int x,y;
		int minDistanceToEdge = Integer.MAX_VALUE;
		int currDistance;
		for(int xInc = -1; xInc<2; xInc++) {
			for(int yInc = -1; yInc<2; yInc++) {
				x = midX;
				y = midY;
				currDistance = 0;
				if (yInc!=0 && xInc!=0) {
					while(img.getPixel(x,y)[0]==0 && currDistance<30) {
						x+=xInc;
						y+=yInc;
						currDistance++;
					}
					currDistance = (int) (Math.sqrt(Math.pow(currDistance*xInc,2)+Math.pow(currDistance*yInc,2)));
					if (currDistance<minDistanceToEdge) {
						minDistanceToEdge = currDistance;
					}
				}
			}
		}
		return Math.max(minDistanceToEdge,2);
	}
	public ArrayList<String> getSubFolderPaths(String rootPath){
		ArrayList<String> subfolderList = new ArrayList<String>();
		File rootDirectory = new File(rootPath);
		String[] directories = rootDirectory.list(new FilenameFilter() {
			  @Override
			  public boolean accept(File current, String name) {
			    return new File(current, name).isDirectory();
			  }
			});
		String path;
		for(int i = 0; i<directories.length; i++) {
			path = rootPath + directories[i] + "\\";
			subfolderList.add(path);
		}		
		return subfolderList;
	}
	public ArrayList<String> getFilepathsInFolder(String folderpath){
		ArrayList<String> files = new ArrayList<String>();
		
		File folder = new File(folderpath);
		String[] fileList = folder.list(new FilenameFilter() {
			  @Override
			  public boolean accept(File current, String name) {
				  if (!new File(current, name).isFile()) {
					  return false;
				  }
				  if (name.endsWith(".csv")) {
					return true;
				  }
				  
			    return false;
			  }
			});
		fileList = orderFilesNumerically(fileList);  
		String path;
		for(int i = 0; i<fileList.length; i++) {
			path = folder + "\\" + fileList[i];
			files.add(path);
		}		
		return files;
	}
	public String[] orderFilesNumerically(String[] files) {
		int[] filenumber = new int[files.length];
		for (int i = 0; i<filenumber.length; i++) {
			filenumber[i] = Integer.parseInt(files[i].replaceAll(".csv", ""));
		}
		Arrays.sort(filenumber);
		for(int i = 0; i<filenumber.length;i++) {
			files[i] = Integer.toString(filenumber[i]) + ".csv";
		}
		return files;
	}

	public double getFPATemp(String filepath) {
		double temperature = -999;
		try {
			BufferedReader br = new BufferedReader(new FileReader(filepath));
			String line = br.readLine();
			Scanner s = new Scanner(line);
			s.useDelimiter("\\s+");
			s.next();
			s.next();
			temperature = s.nextDouble();
			br.close();
			s.close();
		}catch (IOException e) {
			System.out.println(e.getMessage());
		}
		return temperature;
	}
	public int[][] getPixelValuesFromCSV(String filepath){
		int[][] pixels = new int[_width][_height];
		try {
			BufferedReader br = new BufferedReader(new FileReader(filepath));
			String line = br.readLine();
			line = br.readLine();
			Scanner s;
			int x = 0,y=0;
			while (line!=null) {
				x = 0;
				s = new Scanner(line).useDelimiter("\\s+");
				while (s.hasNext()) {
					pixels[x][y] = s.nextInt();
					x++;
				}
				line = br.readLine();
				y++;
			}
			br.close();
		}catch (IOException e) {
			System.out.println(e.getMessage());
			return null;
		}
		return pixels;
	}
	public ImagePlus loadImagePlus(String filepath) {
		if (_print) {
			//System.out.println("loading " + filepath);
		}
		if (filepath.endsWith(".csv")) {
			return getImagePlusFromCSV(filepath);
		}
		else return new ImagePlus(filepath);
	}
	public ImagePlus getImagePlusFromCSV(String filepath) {
		int[][] array = getPixelValuesFromCSV(filepath);
		//java.lang.String title, int width, int height, int nSlices, int bitDepth, int options
		ImageProcessor imp = new ShortProcessor(_width,_height);
		for(int x = 0; x<_width; x++) {
			for(int y = 0; y<_height; y++) {
				imp.putPixel(x,y,array[x][y]);
			}
		}
		ImagePlus image = new ImagePlus("",imp);
		if (_serialise) {
			System.out.println("Serialising " + filepath.replace(".csv",".jpeg"));
			new FileSaver(image).saveAsJpeg(filepath.replace(".csv",".jpeg"));
		}
		return image;
	}
	public static void main(String[] args) {
		double ratio = 0.6667;
		CalibrationParser cp = new CalibrationParser();
		cp.run("D:\\Lepton\\Data\\DEC_16\\Calib_October_17\\Stability\\",ratio);
		cp.run("D:\\Lepton\\Data\\DEC_16\\Calib_October_17\\Calib\\", ratio);
		cp.run("D:\\Lepton\\Data\\APR_17\\Calib_October_17\\Stability\\",ratio);
		cp.run("D:\\Lepton\\Data\\APR_17\\Calib_October_17\\Calib\\", ratio);
	}

}