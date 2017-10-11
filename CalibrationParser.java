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

	public void run(String rootPath,double ratio) {
		//ROI oval represented by box surrounding it; x0,y0 are topleft corner.
		_rootPath = rootPath;
		ArrayList<String> subfolders = getSubFolderPaths(rootPath);
		
		String header = "Folder\tMean\tSigma\tFPATemp\tSigma";
		writeToFile(rootPath+"log.txt",header,false);

		for(String folderpath:subfolders) {
			parseFolder(folderpath,ratio);
		}

	}
	public void parseFolder(String folderpath,double ratio) {
		double signalMean = 0,signalSigma = 0;
		double signal = 0;
		double FPATemp,FPAMean = 0, FPASigma = 0;
		int nFiles = 0;

		int midX = _width/2;
		int midY = _height/2;
		double size;

		ArrayList<String> files = getFilepathsInFolder(folderpath);
		ArrayList<Double> FPAValues = new ArrayList<Double>();
		ArrayList<Double> imageValues = new ArrayList<Double>();
		String outputFilepath = _rootPath+getFoldernameFromPath(folderpath) + ".txt";
		writeToFile(outputFilepath,"",false);
		
		for(String filepath:files) {
			ImagePlus image = loadImagePlus(filepath);
			ImagePlus edgeDetectedImage = _edgeDetector.process(image);
			int roiRadius = getROIRadiusFromEdgeDetectedImage(edgeDetectedImage);
			if (roiRadius>20 || roiRadius<4) {
				System.out.println("Deleting " + filepath);
				delete(filepath);
			}
			else {
				int x1 = midX-roiRadius;
				int y1 = midY-roiRadius;
				size = 2*(roiRadius*ratio);
				OvalRoi roi = new OvalRoi(x1,y1,size,size);
				image.setRoi(roi);
				ImageStatistics stats = image.getProcessor().getStatistics();
				signal = stats.mean;
				FPATemp  
				
				nFiles++;
			}
			writeToFile(outputFilepath,,true);
		}

		
		String line = folderpath + "\t" + signalMean + "\t" + signalSigma + "\t" + FPAMean + "\t" + FPASigma;
		writeToFile(_rootPath+"log.txt",line,true);
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
	public String getFoldernameFromPath(String filepath) {
		String[] arr = filepath.split("\\");
		return arr[arr.length-1];
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
	public double calculateStandardDeviation(double mean, ArrayList<Double> values) {
		//return sample deviation
		double sd = 0;
		for(double x_i:values) {
			sd+=(x_i-mean)*(x_i-mean);
		}
		sd/=(values.size()-1);
		sd = Math.sqrt(sd);
		return sd;
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
		String[] directories = folder.list(new FilenameFilter() {
			  @Override
			  public boolean accept(File current, String name) {
				  if (!new File(current, name).isFile()) {
					  return false;
				  }
				  if (name.endsWith(".jpeg") && _useJPEGs) {
					  return true;
				  }
				  if (name.endsWith(".csv") && (!_useJPEGs || !jpegExistsForCSV(folderpath + "\\" + name))) {
					return true;
				  }
				  
			    return false;
			  }
			});
		String path;
		for(int i = 0; i<directories.length; i++) {
			path = folder + "\\" + directories[i];
			files.add(path);
		}		
		return files;
	}
	public boolean jpegExistsForCSV(String csvFilename) {
		String jpegFilepath = csvFilename.replace(".csv",".jpeg");
		return new File(jpegFilepath).exists();
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
		new CalibrationParser().run("D:\\Lepton\\DEC_16_lepton\\Calib_October_17\\Calib\\",0.667);
	}

}