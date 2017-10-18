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

def run(filepath):
    outname = filepath.split("\\")[-1].replace("RAW.txt","PROCESSED.txt")
    outputpath = os.path.dirname(os.path.realpath(filepath)) + "\\" + outname

    #get binned data
    binnedData = getBinnedData(filepath)
    processedData = []
    processedData.append(['#ITS90 Temp\tITS90 Stdev\tSignal\tSignal Stdev\tFPA Temp\tFPA Stdev\tWeight\tn'])
    for bin in binnedData:
        datapoint = produceDatapoint(bin)
        if datapoint == None:
            pass
        else:
            processedData.append(datapoint)

    sortAscendingData(processedData)
    applyWeights(processedData)

    f = file(outputpath,'w')
    for point in processedData:
        for col in point:
            f.write(str (col) + "\t")
        f.write("\n")
    f.close()

    print("File written to " + outputpath)

def sortAscendingData(processedData):
    #sort in terms of increasing FPA Temperature for each F250 setpoint
    nChanges = 1
    while nChanges>0:
        nChanges = 0
        for i in range(1,len(processedData)-1):
            if abs(processedData[i][0]-processedData[i+1][0])<1:
                if processedData[i][4]>processedData[i+1][4]:
                    temp1 = processedData[i]
                    processedData[i] = processedData[i+1]
                    processedData[i+1] = temp1
                    nChanges+=1

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
        sigma = sigma**0.5
        sigma/=(len(array)-1)
    else:
        sigma = minErr
    return mean,sigma

def getBinnedData(filepath):
    #binned[i] = arrays of data grouped to same F250 temperature
    #binned[i][j] = array binned to same F250 temp and similar FPA temperature
    #binned[i][j][k] = array corresponding to one measurement
    raw = np.genfromtxt(filepath)
    data = []
    for row in raw:
        data.append(np.ndarray.tolist(row))
    sortedData = sortData(data)
    binnedData = []
    for elem in sortedData:
        #==================================
        #fudgery located here- possibly fixed?
        binned = binData(elem)
        for arr in binned:
            binnedData.append(arr)
        #==================================
    return binnedData

def sortData(data):
    sortedData = []
    index = 0
    while index<len(data)-1:
        bin = []
        prevF250Temp = data[index][_ITS90Col_]
        prevFPATemp = data[index][_FPACol_]
        while data[index][_ITS90Col_]==prevF250Temp and index<len(data)-1 and abs(data[index][_FPACol_]-prevFPATemp)<1.5:
            prevF250Temp = data[index][_ITS90Col_]
            prevFPATemp = data[index][_FPACol_]
            bin.append(data[index])
            index+=1
        sortedData.append(bin)
    return sortedData

def binData(sortedDataElem,nBins = 10):
    #return a list of bins containing points with similar FPA temperature
    min,max = getMinMaxFPATemp(sortedDataElem)
    binnedData = []
    for i in range(nBins):
        binnedData.append([])
    for row in sortedDataElem:
        binIndex = int ((row[_FPACol_]-min)/nBins)
        binnedData[binIndex].append(row)
    return binnedData

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

run('D:\\Lepton\\Data\\DEC_16_STABILITY_RAW.txt')
run('D:\\Lepton\\Data\\DEC_16_CALIB_RAW.txt')
run('D:\\Lepton\\Data\\APR_17_STABILITY_RAW.txt')
run('D:\\Lepton\\Data\\APR_17_CALIB_RAW.txt')