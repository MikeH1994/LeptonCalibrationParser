import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;
import ij.gui.NewImage;
import ij.io.FileSaver;
import ij.plugin.*;
import ij.plugin.frame.*;

public class CalibrationParser{
	String _folderpath = "C:\\Users\\Micha\\Desktop\\Scientific\\Lepton Calibration\\DEC_16_lepton\\Calib_October_17\\";
	int _width = 160;
	int _height = 120;
	boolean _useJPEGs = true;
	boolean _serialise = true;
	public void run(String rootPath) {
		ArrayList<String> subfolders = getSubFolderPaths(rootPath);
		ArrayList<String> files;
		ImagePlus image;
		for(String folderpath:subfolders) {
			files = getFilepathsInFolder(folderpath);
			for(String filepath:files) {
				image = loadImagePlus(filepath);
			}
		}
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
				  boolean flag = false;
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
		System.out.println("loading " + filepath);
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
		new CalibrationParser().run("D:\\Lepton\\DEC_16_lepton\\Calib_October_17\\");
	}
}