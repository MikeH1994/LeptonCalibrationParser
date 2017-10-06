import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
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
	public void run(String rootPath,double ratio) {
		//ROI oval represented by box surrounding it; x0,y0 are topleft corner.
		int midX = _width/2;
		int midY = _height/2;
		int x1,y1,roiRadius;
		double size;
		double folderMean = 0, folderSigma = 0;
		double imageMean = 0, imageSigma = 0;
		double sampleDeviation;

		ArrayList<String> subfolders = getSubFolderPaths(rootPath);
		ArrayList<String> files;
		ArrayList<Double> values;
		ArrayList<String> linesToWrite = new ArrayList<String>();
		String lineToWrite;
		ImagePlus image;
		ImagePlus edgeDetectedImage;
		Canny_Edge_Detector edgeDetector = new Canny_Edge_Detector();
		OvalRoi roi;
		ImageStatistics stats;
		
		linesToWrite.add("Folder\tMean\tSigma");
		
		for(String folderpath:subfolders) {
			folderMean = 0;
			folderSigma = 0;
			files = getFilepathsInFolder(folderpath);
			values = new ArrayList<Double>();
			if (_print) {
				System.out.println("Loading " + folderpath);
			}
			for(String filepath:files) {
				image = loadImagePlus(filepath);
				edgeDetectedImage = edgeDetector.process(image);
				roiRadius = getROIRadiusFromEdgeDetectedImage(edgeDetectedImage);
				if (roiRadius>20 || roiRadius<4) {
					System.out.println(filepath + "looks suspicious");
				}
				x1 = midX-roiRadius;
				y1 = midY-roiRadius;
				size = 2*(roiRadius*ratio);
				roi = new OvalRoi(x1,y1,size,size);
				image.setRoi(roi);
				stats = image.getProcessor().getStatistics();
				imageMean = stats.mean;
				imageSigma = stats.stdDev;
				folderMean+=imageMean;
				folderSigma+=imageSigma*imageSigma;
				values.add(imageMean);
			}
			folderMean/=files.size();
			folderSigma = Math.sqrt(folderSigma);
			sampleDeviation = calculateStandardDeviation(folderMean, values);
			lineToWrite = folderpath + "\t" + folderMean + "\t" + sampleDeviation;
			linesToWrite.add(lineToWrite);
		}
		writeToFile(rootPath,"log.txt",linesToWrite);
	}
	public void writeToFile(String rootFolder, String name, ArrayList<String> linesToWrite) {
		try {
			PrintWriter  out = new PrintWriter(rootFolder + name,"UTF-8");
			for(String line:linesToWrite) {
				out.write(line + "\n");
			}
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
	@SuppressWarnings("resource")
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
		new CalibrationParser().run("D:\\Lepton\\DEC_16_lepton\\Calib_October_17\\",0.667);
	}

}