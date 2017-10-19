import numpy as np
import os

#specifying columns in raw data
_ITS90Col_ = 0
_ITS90ErrCol_ = 1
_SignalCol_ = 2
_SignalSigmaCol_ = 3
_FPACol_ = 4

_MINSIGNALERR_ = 5
_MINFPAERR_ = 0.005
_removeHeatup = False


def run(filepath):
    #get binned data
    data = getData(filepath)
    if _removeHeatup:
        outname = filepath.split("\\")[-1].replace("RAW","RAW_NO_HEATING")
        outputpath = os.path.dirname(os.path.realpath(filepath)) + "\\" + outname
        writeToFile2d(outputpath,data)
        print("File written to " + outputpath)

    processedData = []
    processedData.append(['#ITS90 Temp\tITS90 Stdev\tSignal\tSignal Stdev\tFPA Temp\tFPA Stdev\tWeight\tn'])
    for setpoint in data:
        datapoint = produceDatapoint(setpoint)
        if datapoint == None:
            pass
        else:
            processedData.append(datapoint)

    outname = filepath.split("\\")[-1].replace("RAW","PROCESSED")
    outputpath = os.path.dirname(os.path.realpath(filepath)) + "\\" + outname
    writeToFile(outputpath,processedData)
    print("File written to " + outputpath)

def writeToFile(outputpath, data):
    f = file(outputpath,'w')
    for point in data:
        for col in point:
            f.write(str (col) + "\t")
        f.write("\n")
    f.close()

def writeToFile2d(outputpath, data):
    f = file(outputpath, 'w')
    for setpoint in data:
        for point in setpoint:
            for col in point:
                f.write(str(col) + "\t")
            f.write("\n")
    f.close()

def applyWeights(processedData):
    tempIndex = 1
    prevF250Temp = processedData[tempIndex][0]
    prevFPATemp = processedData[tempIndex][4]
    while tempIndex<len(processedData):
        startVal = tempIndex
        prevF250Temp = processedData[tempIndex][0]
        prevFPATemp = processedData[tempIndex][4]
        while tempIndex<len(processedData) and abs(prevF250Temp-processedData[tempIndex][0])<1 and abs(prevFPATemp-processedData[tempIndex][4])<1:
            prevF250Temp = processedData[tempIndex][0]
            prevFPATemp = processedData[tempIndex][4]
            tempIndex+=1
        endVal = tempIndex

        for i in range(startVal,endVal):
            processedData[i][6] = 1.0/(endVal-startVal)

def produceDatapoint(FPABin):
    if (len(FPABin))==0:
        return None
    F250 = FPABin[0][_ITS90Col_]        # all values in bin have same F250 temp and err
    F250Err = FPABin[0][_ITS90ErrCol_]  #
    signal, signalStdev = getAverageAndStdev(FPABin,_SignalCol_,minErr = _MINSIGNALERR_)
    FPA, FPAStdev = getAverageAndStdev(FPABin,_FPACol_,minErr = _MINFPAERR_)
    nMeasurements = len(FPABin)

    return [F250,F250Err,signal,signalStdev,FPA,FPAStdev,0,nMeasurements]

def getAverageAndStdev(array,col,minErr = 0):
    mean = 0
    sigma = 0

    for point in array:
        mean+=point[col]
    mean/=len(array)
    if len(array)>1:
        for point in array:
            sigma+=(point[col]-mean)**2
        sigma/=(len(array)-1)
        sigma = sigma**0.5
    else:
        sigma = minErr
    return mean,sigma

def getData(filepath):
    #binned[i] = arrays of data grouped to same F250 temperature
    #binned[i][j] = array binned to same F250 temp and similar FPA temperature
    #binned[i][j][k] = array corresponding to one measurement
    raw = np.genfromtxt(filepath)
    data = []
    for row in raw:
        data.append(np.ndarray.tolist(row))
    sortedData = sortData(data)
    if _removeHeatup:
        for i in range(len(sortedData)):
            sortedData[i] = removeHeatupPeriod(sortedData[i])
    return sortedData

def removeHeatupPeriod(measurementSet):
    if len(measurementSet)<2:
        return measurementSet
    prevFPA = 0
    startVal = 0
    medianValue = measurementSet[len(measurementSet)/2][_FPACol_]
    while abs(measurementSet[startVal][_FPACol_]-medianValue)>0.05 and startVal<len(measurementSet)-1:
        startVal+=1
    measurementSet = measurementSet[startVal:-1]
    return measurementSet

def sortData(data):
    #sortAscendingData(data)
    sortedData = []
    index = 0
    while index<len(data)-1:
        bin = []
        prevF250Temp = data[index][_ITS90Col_]
        prevFPATemp = data[index][_FPACol_]
        #TODO: change this here
        while data[index][_ITS90Col_]==prevF250Temp and index<len(data)-1 and abs(data[index][_FPACol_]-prevFPATemp)<1.5:
            prevF250Temp = data[index][_ITS90Col_]
            prevFPATemp = data[index][_FPACol_]
            bin.append(data[index])
            index+=1
        if len(bin)==1:
            print "uhoh"
            print(data[index],data[index-1],index)
        sortedData.append(bin)
    return sortedData

def getMinMaxFPATemp(sortedDataElem):
    min = 100
    max = 0
    for arr in sortedDataElem:
        FPATemp = arr[_FPACol_]
        if FPATemp<min:
            min = FPATemp
        if FPATemp>max:
            max = FPATemp
    return min,max

run('D:\\Lepton\\Data\\RAW_NO_HEATING_DEC_16_STABILITY.txt')
run('D:\\Lepton\\Data\\RAW_NO_HEATING_DEC_16_CALIB.txt')
run('D:\\Lepton\\Data\\RAW_NO_HEATING_APR_17_STABILITY.txt')
run('D:\\Lepton\\Data\\RAW_NO_HEATING_APR_17_CALIB.txt')