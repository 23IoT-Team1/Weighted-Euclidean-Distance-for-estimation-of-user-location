

import java.util.*;
import java.util.stream.Collectors;

// 일단 weighted distance 로 평균과 분산을 사용한버전

public class similarity {

	
	int rpNum = 49; //rp의 개수
    /**
     *
     * @param dataSet : DB에서 가져온 모든 ap List
     * @param dto : 유저가 보낸 현재 위치 정보
     * @param dataSetNum : DB에 저장되어있는 ap row 개수
     * @return : rp
     */
    public String findPosition(List<ap> dataSet,
                               FindPositionRequestDto dto,
                               Integer dataSetNum) {

        int numOfAp = 5; // 수신할 wifi 신호의 개수
        int k = 6; // KNN 알고리즘에 사용되는 k
        double revision = 0.6; // 보정값. 우리는 가장 강한 numOfAp개의 신호를 사용한다. 웬만한 경우에는 같은 위치에서 scan할 경우 가장 강한 wifi 신호도
        // 똑같겠지만, 그 순간 잠시 이상이 생겨 가장 강한 wifi의 신호가 달라질 수도 있기 때문에 보정값을 넣는다.

        testCaseRp testCase = new testCaseRp(dto); // 현재 측정된 값
        
        ArrayList<calc> calc_list;
        ArrayList<calc> filtered_calc_list; // filtering할 calc_list
        String calc_answer; // 가장 유사도가 높은 rp를 저장할 변수

        calc_list = calculateAvgAndVar(dataSet, testCase, dataSetNum, numOfAp);

        // 분산과 표준편차를 이용한 weightedEuclideanDistance 구하기
        weightedEuclideanDistance(calc_list, testCase, numOfAp, k);

        // 수많은 calc_list를 bssidCount값과 revision 보정값을 이용해서 필터링
        filtered_calc_list = filterCalcList(calc_list, revision);


        //Weighted KNN을 사용하여 filtered_calc_list에서 K개를 뽑아서 비교 후 정답 도출
        calc_answer = weightedKNN(filtered_calc_list, k);

        return calc_answer; // grid_point 리턴

    }

    public String weightedKNN(ArrayList<calc> filtered_calc_list, int k) {

        //euclidean distance순으로 정렬
        Collections.sort(filtered_calc_list, new Comparator<calc>() {
            public int compare(calc a, calc b) {
                if (a.weightedEuclideanDistance > b.weightedEuclideanDistance)
                    return 1;
                else
                    return -1;
            }
        });

        // 예외처리
        List<calc> k_calc_list;
        if (filtered_calc_list.size() <= k) {
            k_calc_list = filtered_calc_list; // filtered_calc_list 크기가 k보다 작거나 같을 경우 모든 요소를 포함하는 서브리스트로 설정
        } else {
            k_calc_list = filtered_calc_list.subList(0, k); // filtered_calc_list 크기가 k보다 클 경우 시작부터 k-1까지의 요소를 포함하는 서브리스트로 설정
        }
        
        //bssidCount를 가중치로 설정하여 weightedEuclideanDistance 보완
        int index = 0;
        double minDistance = k_calc_list.get(0).weightedEuclideanDistance/k_calc_list.get(0).bssidCount;
        for(int i = 1;i<k_calc_list.size();i++)
        {
        	if(k_calc_list.get(i).weightedEuclideanDistance/k_calc_list.get(i).bssidCount < minDistance)
        	{
        		minDistance=k_calc_list.get(i).weightedEuclideanDistance/k_calc_list.get(i).bssidCount;
        		index=i;
        	}
        }

        return k_calc_list.get(index).rp;
    }

    public ArrayList<calc> filterCalcList(ArrayList<calc> calc_list, double revisionValue) {

        ArrayList<calc> filtered_calc_list = new ArrayList<calc>();

        int maxCount = calc_list.get(0).bssidCount;

        for (int i = 0; i < calc_list.size(); i++) {
            if (calc_list.get(i).bssidCount > maxCount * revisionValue)
                filtered_calc_list.add(calc_list.get(i));
            else
                break;
        }

        return filtered_calc_list;

    }

    public void weightedEuclideanDistance(ArrayList<calc> calc_list, testCaseRp testCase, int numOfAp, int k) {


       	//현재 위치의 bssid 값의 배열
    	String[] testCaseBssid = new String[numOfAp];
        for (int i = 0; i < numOfAp; i++) {
            testCaseBssid[i] = testCase.getBssid(i);
        }
        
        for(calc c : calc_list)
        {
        	for(group g : c.groups) 
        	{
        		if(Arrays.asList(testCaseBssid).contains(g.bssid))
        		{
        			c.bssidCount++;
        			
        			// Weighted Euclidean distance 구함. testCase rss값과 데이터 그룹의 평균 rss값의 차를 구한다음 제곱을하고 그룹 분산 rss값으로 나누어준다
                    c.weightedEuclideanDistance += Math.sqrt(
                    		Math.pow(Double.parseDouble(testCase.getRssFromBssid(g.bssid).replace("dbm", "").trim()) - g.rssAvg,2) / g.rssVar);
                }
        	}
       	}
        
        // 현재 위치와의 유사도 순으로 정렬. 유사도의 기준은 일치하는 bssid 값의 수 & WeightedEuclideandistance의 값 
        calc_list.sort(Comparator.reverseOrder());
    }

    public ArrayList<calc> calculateAvgAndVar(List<ap> dataSet, testCaseRp testCase, Integer dataSetNum, int numOfAp)
    {

    	ArrayList<calc> calc_list = new ArrayList<calc>();
    	
    	for(int i=0;i<rpNum;i++) 
    	{
    		//calc 4층과 5층으로 나누어서 생성하고 calc_list에 add.
    		if(i<49)
    			calc_list.add(new calc("4-"+Integer.toString(i+1)));
    		else
    			calc_list.add(new calc("5-"+Integer.toString(i-48)));
    	}
    	
    	for(ap data : dataSet)
    	{
    		for(calc c : calc_list) 
    		{
    			// 하나씩 가져온 data와 calc의 rp를 비교, data.rp와 c.rp가 같으면 해당 data의 rss값을 calc에 추가한다.
    			if(data.rp.equalsIgnoreCase(c.rp))
    			{
    				//해당 rp의 calc객체의 size가 0이면 아직 calc에는 bssid가 저장되어 있지 않으므로 추가해준다.
    				if(c.groups.size()==0) 
    				{
    					c.groups.add(new group(data.bssid, Double.parseDouble(data.rss.replace("dbm", "").trim() ) ));
    				}else
    				{
    					//calc객체를 순회하며 data.bssid와 bssid가 같은 것이 있는지 검색
    					for(group g : c.groups)
    					{
    						//이미 bssid 그룹이 만들어져 있다면 rss를 추가해준다.
    						if(g.bssid.equalsIgnoreCase(data.bssid)) 
    						{
    							g.addRss(Double.parseDouble(data.rss.replace("dbm", "").trim()));
    						}else
    						{
    							//data.bssid의 그룹이 만들어져 있지 않다면 그룹을 추가해준다.
    	    					c.groups.add(new group(data.bssid, Double.parseDouble(data.rss.replace("dbm", "").trim() ) ));
    						}
    					}
    				}
    				break;
    			}
    		}
    	}
    	
    	for(calc c : calc_list) 
    	{
    		for(group g: c.groups) 
    		{
    			g.computeAvgAndVar();
    		}
    	}
    	
    	return calc_list;
    	
    }

}



class group
{
	String bssid;
	ArrayList<Double> rssValues = null;
	double rssSum=0;
	double rssAvg=0;
    double rssVar=0;
    
    public group(String bssid,double rss) 
    {
       this.bssid=bssid;
       rssValues.add(rss);
   	   rssSum+=rss;
    }
    
    public void addRss(double rss) 
    {
        rssValues.add(rss);
    	this.rssSum+=rss;
    }
    
    public void computeAvgAndVar() 
    {
    	//이 bssid 그룹의 rss 평균
    	rssAvg = rssSum / rssValues.size();
    	
    	//이 bssid 그룹의 rss 분산
    	for(double rss: rssValues) 
    	{
    		rssVar += Math.pow(rss - rssAvg,2);
    	}
    	rssVar = rssVar / rssValues.size();
    }

}

//calc 객체는 해당 rp의 모든 bssid별 ap data를 다루기 위해 사용.
class calc implements Comparable<calc> {
    String rp = null;
    ArrayList<group> groups;
    
    int bssidCount = 0;
    double weightedEuclideanDistance=0;
    
    public calc(String rp) 
    {
       this.rp = rp;
       groups=null;
    }
    @Override
    public int compareTo(calc c) {
        if (c.bssidCount < bssidCount) {
            return 1;
        } else if (c.bssidCount > bssidCount) {
            return -1;
        } else {
            if (c.weightedEuclideanDistance > weightedEuclideanDistance)
                return 1;
            else if (c.weightedEuclideanDistance < weightedEuclideanDistance)
                return -1;
        }
        return 0;
    }
}


class ap {
    String ssid;
    String bssid;
    String rss;
    String rp = null;
    String place = null;

    public ap(String ssid,
              String bssid,
              String rss) {
        this.ssid = ssid;
        this.bssid = bssid;
        this.rss = rss;
    }
}



// 알고싶은 현재 위치에 대한 정보..
class testCaseRp {

    List<ap> aps;

    public testCaseRp(FindPositionRequestDto dto) {

        // 현재 측정되는 ap List 생성
        this.aps = dto.getAps().stream()
                .map(ap -> new ap(
                                ap.getSsid(),
                                ap.getBssid(),
                                ap.getRss()
                        )
                )
                .collect(Collectors.toList());
    }

    public String getBssid(int index) {
        return aps.get(index).bssid;
    }

    public String getRssFromBssid(String bssid)
    {
        for(ap ap:aps)
        {
            if(ap.bssid.equals(bssid))
                return ap.rss;
        }
        return null;
    }

}