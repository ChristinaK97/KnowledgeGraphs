from pytrie import SortedStringTrie as Trie

t = Trie(an=0, ant=1, all=2, allot=3, alloy=4, aloe=5, are=6, be=7)

"""
print(t.keys(prefix='al'))
# ['all', 'allot', 'alloy', 'aloe']
print(t.items(prefix='an'))
# [('an', 0), ('ant', 1)]
print(t.longest_prefix('antonym'))
'ant'
print(t.longest_prefix_item('allstar'))
# ('all', 2)
print(t.longest_prefix_value('area', default='N/A'))
# 6
# t.longest_prefix('alsa')
# Traceback (most recent call last):
#    ...
# KeyError
print(t.longest_prefix_value('alsa', default=-1))
# -1
print(list(t.iter_prefixes('allotment')))
# ['all', 'allot']
print(list(t.iter_prefix_items('antonym')))
# [('an', 0), ('ant', 1)]
"""
l  = [1]
t = Trie(ACE=l, CAD="Coronary Artery Disease", CA="Calcium", LM="Left Main Artery")
header = "ACECA"

print(
    t.longest_prefix_item(header)
)

