function PPG_peak_locs = Peak_detection(PPG,fs)


%% predspracovanie 

    % bandpass filter
    [b, a] = butter(2,[0.5 8]/(fs/2)); 
    filt_PPG = filtfilt(b, a, PPG); 


    %normalizácia
    filt_PPG = filt_PPG ./ std(filt_PPG); 
    filt_PPG = filt_PPG - mean(filt_PPG);
    
    
    Y = filt_PPG;


    %berieme iba pozitívne časti signálu
    Z = Y;
    Z(Z < 0) = 0; 

    %umocnenie signálu
    y = (Z).^2; 


%% feature extraction 

    % prvý kĺzavý priemer
    W_1 = round(0.111 * fs);  %dĺžka okna systolického peaku 
    Average_peak = movmean(y,W_1);
    
    % druhý kĺzavý priemer 
    W_2 = round(0.667 * fs); %dĺžka okna PPG pulzu
    Average_beat = movmean(y,W_2);


%% classification - thresholding, klasifikácia pomocou prahovania

    beta = 0.02; 
    z_bar = mean(y);
    alfa = beta * z_bar; 
    TH_1 = Average_beat + alfa;

    Blocks = zeros(size(Average_peak)); 
    for nn = 1:length(Average_peak)
        if Average_peak(nn) > TH_1(nn) 
            Blocks(nn) = 0.1;
        else
         
        end
    end
    
    % V každom bloku hľadáme začiatok a koniec pulznej krivky
    num_blocks = 0;
    block_on = NaN(size(Average_peak));
    block_off = NaN(size(Average_peak));
    if any(Blocks > 0) 
        for nn = 1:length(Average_peak)
            if nn == 1 && Blocks(nn) > 0
               
               num_blocks = num_blocks + 1; 
               block_on(num_blocks,1) = nn;
            elseif nn == length(Average_peak) && Blocks(nn) > 0
               
                block_off(num_blocks,1) = nn;
            else
                if nn > 1
                    if Blocks(nn-1) == 0 && Blocks(nn) > 0 
                        num_blocks = num_blocks + 1;
                        block_on(num_blocks,1) = nn;
                    elseif Blocks(nn-1) > 0 && Blocks(nn) == 0 
                        block_off(num_blocks,1) = nn;
                    end
                end
            end
        end
    else

    return
    end
    
    block_on(isnan(block_on)) = []; 
    block_off(isnan(block_off)) = []; 
    
   
    % v každom bloku hľadáme maximum (systolický peak)
    for jj = 1:num_blocks
        block_idx = [block_on(jj,1):block_off(jj,1)];
        [~,I] = max(y(block_idx));
        S_peaks(jj,1) = block_on(jj,1) + I - 1;
    end
    
     
    
    PPG_peak_locs = S_peaks;
        
end