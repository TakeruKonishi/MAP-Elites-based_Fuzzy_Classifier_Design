# -*- coding: utf-8 -*-
"""
Created on Thu Jun 13 16:47:00 2024

@author: konishi
"""

import pandas as pd
import numpy as np
import matplotlib.pyplot as plt

plt.rc('font', family='Times New Roman')

addFontSize = 80

sep = "\\"

Dataset = 'bupa'

folder = 'results' + sep + 'ME' + sep + Dataset + sep + Dataset + sep

numberofclasses = 2

trial = ['00']

trial_str = "".join(trial)

data = pd.read_csv(folder + "trial" + trial_str + sep + 'results.csv')

# グリッドの設定
min_NR = numberofclasses
max_NR = data['NR'].max()
min_RL = numberofclasses-1
max_RL = data['RL'].max()

# グリッドサイズの計算
grid_size = 1
num_rows = int(np.ceil(max_NR - min_NR)) + 1
num_cols = int(np.ceil(max_RL - min_RL)) + 1

# ヒートマップ用のグリッドを初期化
heatmap = np.full((num_rows, num_cols), np.nan)

# データをグリッドにマッピング
for _, row in data.iterrows():
    if row['NR'] >= numberofclasses and row['RL'] >= numberofclasses-1:
        nr_idx = int(row['NR'] - min_NR)
        rl_idx = int(row['RL'] - min_RL)
        heatmap[nr_idx, rl_idx] = row['train']
    
heatmap = heatmap.T

heatmap = np.ma.masked_invalid(heatmap)

#colorbar_min = np.nanmin(average_heatmap)
#colorbar_max = np.nanmax(average_heatmap)
colorbar_min = 0.0
colorbar_max = 1.0

# ヒートマップの描画
plt.figure(figsize=(18, 36))
img = plt.imshow(heatmap, cmap='jet_r', origin='lower', interpolation='none',
                 extent=[min_NR, max_NR, min_RL, max_RL], vmin=colorbar_min, vmax=colorbar_max, aspect='auto')
cbar = plt.colorbar(img, label='Error Rate', aspect=30)
cbar.ax.tick_params(labelsize=addFontSize, pad=15)
cbar.set_label('Error Rate', fontsize=100, labelpad=20)

cbar_ticks = np.arange(colorbar_min, colorbar_max + 0.1, 0.1)
cbar.set_ticks(cbar_ticks)
cbar.set_ticklabels(['{:.1f}'.format(tick) for tick in cbar_ticks])

plt.xticks([numberofclasses, 10, 20, 30, 40, 50, 60], fontsize=addFontSize)
plt.yticks([numberofclasses - 1, 20, 40, 60, 80, 100, 120, 140, 160, 180], fontsize=addFontSize)
#plt.yticks([numberofclasses - 1, 20, 40, 60, 80, 100, 120, 140, 160, 180, 200, 220, 240], fontsize=addFontSize)
#plt.yticks([numberofclasses - 1, 20, 40, 60, 80, 100, 120, 140, 160, 180, 200, 220, 240, 260, 280, 300, 320, 340, 360, 380], fontsize=addFontSize)
plt.grid(which='both', color='gray', linestyle='-', linewidth=0.5)
plt.xlabel('Number of Rules', fontsize=100, labelpad=20)
plt.ylabel('Total Rule Length', fontsize=100, labelpad=20)
plt.tight_layout()
plt.savefig("vehicleME_avetst.png", format="png", dpi=400, bbox_inches='tight', pad_inches=0)
plt.show()

