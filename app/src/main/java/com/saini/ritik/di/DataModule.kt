package com.saini.ritik.di

import com.saini.ritik.data.source.AppDataSource
import com.saini.ritik.pocketbase.PocketBaseDataSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    /** To switch backend: bind a different implementation here */
    @Binds
    @Singleton
    abstract fun bindDataSource(impl: PocketBaseDataSource): AppDataSource
}