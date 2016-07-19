#!/bin/bash

cd /Users/mk/Library/Application\ Support

for i in `seq 101 500`;
	do
	dir=BS_$i
	mkdir $dir
	cp -av BS_/* $dir/
done  