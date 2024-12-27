# -*- coding: utf-8 -*-
"""
Created on Thu Jun 13 16:27:00 2024

@author: konishi
"""

import pandas as pd
import statistics


sep = "\\"

folder = 'results' + sep + 'ME' + sep + 'bupa' + sep 

Dataset = 'bupa'

"""
function SummaryOneTrial
this function return results(number of solutions, max rule number, max rule length, min error rate for train, appropriate error rate for test)
for one trial.
@param Dataset : String
@param trial : String , it is the trial number. 
return dictionary of: 
           number of solutions,
           max the number of rule,
           max rule length,
           min error rate for the training data,
           appropriate error rate for the test data
"""
def SummaryOneTrial(Dataset, trial):
    
    #最終解選択手法：学習用データで最も良い解　→　RLmin →　Covermax →　RWmin
    df_results = pd.read_csv(folder + "trial" + trial + sep + 'results.csv')
    
    numberofsolutions = len(df_results.drop_duplicates(subset=['train', 'NR', 'RL', 'Cover', 'RW']).index)
    
    maxRuleNum = max(df_results.NR)
    
    maxRuleLength = max(df_results.RL)

    TraError = min(df_results.train)
    
    # trainの値が最も小さい行を抽出
    min_train = df_results[df_results['train'] == df_results['train'].min()]
    
    # 最もNRが小さい行を抽出
    selected_row = min_train[min_train['NR'] == min_train['NR'].min()]
    
    # NRが同じ場合はRLが最小の行を抽出
    if len(selected_row) > 1:
        selected_row = selected_row[selected_row['RL'] == selected_row['RL'].min()]
    
    # RLが同じ場合はCoverが最大の行を抽出
    if len(selected_row) > 1:
        selected_row = selected_row[selected_row['Cover'] == selected_row['Cover'].max()]
        
    # Coverが同じ場合は平均ルール重みが最小の行を抽出
    if len(selected_row) > 1:
        selected_row = selected_row[selected_row['RW'] == selected_row['RW'].min()]
        
    target_pop = selected_row['pop'].iloc[0]
    
    TstError = df_results.loc[df_results['pop'] == target_pop, 'test'].values[0]
    
    return {"numberofsolutions" : numberofsolutions, "ruleNum" : maxRuleNum, "rulelength" : maxRuleLength, "TraError" : TraError, "TstError" : TstError}  
    
# make trial number rr = {0,1,2}, cc = {0,1,...9}
trial = [str(rr) + str(cc) for rr in range(3) for cc in range(10)]

results = list(map(lambda x : SummaryOneTrial(Dataset, x), trial))

numberofsolutions = statistics.mean([results[trial]["numberofsolutions"] for trial in range(len(results))])

numberofsolutionsstd = statistics.stdev([results[trial]["numberofsolutions"] for trial in range(len(results))])

ruleNum = statistics.mean([results[trial]["ruleNum"] for trial in range(len(results))])

ruleNumstd = statistics.stdev([results[trial]["ruleNum"] for trial in range(len(results))])

ruleLength = statistics.mean([results[trial]["rulelength"] for trial in range(len(results))])

ruleLengthstd = statistics.stdev([results[trial]["rulelength"] for trial in range(len(results))])

TraError = statistics.mean([results[trial]["TraError"] for trial in range(len(results))])

TraErrorstd = statistics.stdev([results[trial]["TraError"] for trial in range(len(results))])

TstError = statistics.mean([results[trial]["TstError"] for trial in range(len(results))])

TstErrorstd = statistics.stdev([results[trial]["TstError"] for trial in range(len(results))])


# ----print result ----

print("Average the number of solutions : " + str(numberofsolutions))

print("std the number of solutions : " + str(numberofsolutionsstd))

print("Average the number of rule : " + str(ruleNum))

print("std the number of rule : " + str(ruleNumstd))

print("Average the length of rule : " + str(ruleLength))

print("std the length of rule : " + str(ruleLengthstd))

print("Average error rate for the training data : " + str(TraError))

print("std error rate for the training data : " + str(TraErrorstd))

print("Average error rate for the test data : " + str(TstError))

print("std error rate for the test data : " + str(TstErrorstd))






