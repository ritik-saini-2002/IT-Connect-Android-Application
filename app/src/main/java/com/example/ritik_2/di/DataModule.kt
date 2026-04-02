package com.example.ritik_2.di

import com.example.ritik_2.data.source.AppDataSource
import com.example.ritik_2.pocketbase.PocketBaseDataSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    /** To switch database: bind a different implementation here */
    @Binds @Singleton
    abstract fun bindDataSource(impl: PocketBaseDataSource): AppDataSource
}